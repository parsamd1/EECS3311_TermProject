package avsa.agent;

import avsa.infrastructure.AppConfig;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.UserLocation;
import com.anthropic.models.messages.WebFetchTool20250910;
import com.anthropic.models.messages.WebSearchResultBlock;
import com.anthropic.models.messages.WebSearchTool20250305;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calls Claude through the official Anthropic Java SDK. */
public final class ClaudeClient implements LLMClient {

    /** Web searches can take a while; a request that takes longer than this counts as a timeout. */
    static final Duration TIMEOUT = Duration.ofSeconds(120);
    /** How many times a long search turn that the server paused is resumed. */
    static final int MAX_CONTINUATIONS = 3;

    private final AnthropicClient client;
    private final String modelId;

    public ClaudeClient(AppConfig config) {
        this(AnthropicOkHttpClient.builder()
                .apiKey(config.llmApiKey().orElseThrow(() ->
                        new IllegalStateException("No API key configured. Set ANTHROPIC_API_KEY in .env.")))
                .timeout(TIMEOUT)
                .maxRetries(1)
                .build(), config.llmModelId());
    }

    ClaudeClient(AnthropicClient client, String modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(modelId)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessage(request.user());
        request.webSearchOptions().ifPresent(options -> {
            params.addTool(webSearchTool(options));
            if (options.maxFetches() > 0) {
                params.addTool(WebFetchTool20250910.builder()
                        .maxUses((long) options.maxFetches())
                        .allowedDomains(options.allowedDomains())
                        .maxContentTokens((long) options.maxFetchTokens())
                        .build());
            }
        });

        StringBuilder text = new StringBuilder();
        Set<String> sources = new LinkedHashSet<>();
        long inputTokens = 0;
        long outputTokens = 0;
        long searches = 0;
        String stopReason = "";
        try {
            for (int turn = 0; turn <= MAX_CONTINUATIONS; turn++) {
                Message message = client.messages().create(params.build());
                collect(message.content(), text, sources);
                inputTokens += message.usage().inputTokens();
                outputTokens += message.usage().outputTokens();
                searches += message.usage().serverToolUse().map(s -> s.webSearchRequests()).orElse(0L);
                stopReason = message.stopReason().map(StopReason::toString).orElse("");
                if (!message.stopReason().map(StopReason.PAUSE_TURN::equals).orElse(false)) {
                    break;
                }
                params.addMessage(message);
            }
        } catch (AnthropicException e) {
            throw new LLMUnavailableException("The language model could not be reached: " + e.getMessage(), e);
        }
        return new LLMResponse(text.toString(), sources, inputTokens, outputTokens, searches, stopReason);
    }

    private static WebSearchTool20250305 webSearchTool(WebSearchOptions options) {
        return WebSearchTool20250305.builder()
                .maxUses((long) options.maxUses())
                .allowedDomains(options.allowedDomains())
                .userLocation(UserLocation.builder()
                        .city(options.city())
                        .region(options.region())
                        .country(options.country())
                        .build())
                .build();
    }

    private static final Pattern ABSOLUTE_LINK = Pattern.compile("https?://[^\\s)\"'<>\\]]+");
    private static final Pattern RELATIVE_LINK = Pattern.compile("\\]\\((/[^\\s)]+)\\)|href=\"(/[^\"]+)\"");

    /**
     * The links on a fetched page. A results page links to the listings it shows, so a listing found there
     * counts as coming from the page.
     */
    static Set<String> linksIn(String pageUrl, String text) {
        Set<String> links = new LinkedHashSet<>();
        Matcher absolute = ABSOLUTE_LINK.matcher(text);
        while (absolute.find()) {
            links.add(absolute.group());
        }
        URI page = URI.create(pageUrl);
        Matcher relative = RELATIVE_LINK.matcher(text);
        while (relative.find()) {
            String path = relative.group(1) != null ? relative.group(1) : relative.group(2);
            links.add(page.getScheme() + "://" + page.getHost() + path);
        }
        return links;
    }

    /** Text from every text block, and every URL that a search returned, a fetch read or a citation points to. */
    private static void collect(List<ContentBlock> content, StringBuilder text, Set<String> sources) {
        for (ContentBlock block : content) {
            block.text().ifPresent(t -> {
                text.append(t.text());
                t.citations().ifPresent(citations -> {
                    for (TextCitation citation : citations) {
                        citation.webSearchResultLocation().ifPresent(location -> sources.add(location.url()));
                    }
                });
            });
            block.webSearchToolResult().ifPresent(result -> result.content().resultBlocks().ifPresent(results -> {
                for (WebSearchResultBlock r : results) {
                    sources.add(r.url());
                }
            }));
            block.webFetchToolResult().ifPresent(result -> result.content().webFetchBlock().ifPresent(page -> {
                sources.add(page.url());
                page.content().source().text().ifPresent(body -> sources.addAll(linksIn(page.url(), body.data())));
            }));
        }
    }
}
