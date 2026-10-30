package avsa.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Path;

/** Wraps the third-party Jackson library behind the small interface the application needs. */
public class JsonMapper {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public <T> T read(Path file, Class<T> type) throws IOException {
        return mapper.readValue(file.toFile(), type);
    }
}
