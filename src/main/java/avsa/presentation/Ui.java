package avsa.presentation;

import avsa.domain.Money;
import avsa.domain.PriceRange;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Small helpers shared by the tabs, so they look the same and parse input the same way. */
final class Ui {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.CANADA)
            .withZone(ZoneId.systemDefault());

    private Ui() {
    }

    static Label heading(String text) {
        return styled(new Label(text), "heading");
    }

    static Label text(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    static Label muted(String text) {
        return styled(text(text), "muted");
    }

    static Label warning(String text) {
        return styled(text("Warning: " + text), "warning");
    }

    static Label error(String text) {
        return styled(text(text), "error");
    }

    static Label ok(String text) {
        return styled(text(text), "ok");
    }

    /** Says who wrote a piece of text: the AI, or a template because the AI was unavailable (F-all, 2.2). */
    static Label source(boolean ai, String fallbackText) {
        return ai ? styled(new Label("AI"), "badge-ai") : styled(new Label(fallbackText), "badge-fallback");
    }

    static <T extends Node> T styled(T node, String... classes) {
        node.getStyleClass().addAll(classes);
        return node;
    }

    /** A titled box with a border. */
    static VBox card(String title, Node... children) {
        VBox box = new VBox(6);
        box.getStyleClass().add("card");
        if (title != null) {
            box.getChildren().add(styled(new Label(title), "card-title"));
        }
        box.getChildren().addAll(children);
        return box;
    }

    static VBox column(Node... children) {
        VBox box = new VBox(10, children);
        box.setPadding(new Insets(16));
        return box;
    }

    static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }

    static void row(GridPane grid, String label, Node field) {
        int r = grid.getRowCount();
        Label name = new Label(label);
        name.setMinWidth(Region.USE_PREF_SIZE);
        grid.add(name, 0, r);
        grid.add(field, 1, r);
    }

    /** "LOW_BALLER" becomes "Low baller"; short names such as SUV and EV stay in capitals. */
    static String label(Enum<?> value) {
        String name = value.name();
        if (name.length() <= 3) {
            return name;
        }
        String words = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static <E extends Enum<E>> ComboBox<E> choice(Class<E> type, E initial) {
        ComboBox<E> box = new ComboBox<>();
        box.getItems().addAll(Arrays.asList(type.getEnumConstants()));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : label(value);
            }

            @Override
            public E fromString(String text) {
                return null;
            }
        });
        box.setValue(initial);
        return box;
    }

    static <S> TableColumn<S, String> col(String title, Function<S, String> value, double width) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    /** A "#" column numbering the rows from 1. */
    static <S> TableColumn<S, String> rank(double width) {
        TableColumn<S, String> column = col("#", row -> "", width);
        column.setCellFactory(c -> new TableCell<S, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
        return column;
    }

    static <S> TableView<S> table(double height, List<TableColumn<S, String>> columns) {
        TableView<S> table = new TableView<>();
        table.getColumns().addAll(columns);
        table.setPrefHeight(height);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(muted("Nothing here yet."));
        return table;
    }

    /** "$28,900" or "-$290". */
    static String cad(Money m) {
        long dollars = Math.round(m.toDouble());
        return (dollars < 0 ? "-$" : "$") + String.format(Locale.ROOT, "%,d", Math.abs(dollars));
    }

    static String cad(PriceRange r) {
        return cad(r.low()) + " - " + cad(r.high());
    }

    /** "$24,500", "24500" or "24,500" in Canadian dollars; null when blank. */
    static Money parseMoney(String text, String field) {
        String digits = text == null ? "" : text.replaceAll("[$,\\s]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Money.cad(Double.parseDouble(digits));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be an amount in dollars, such as 24,500.");
        }
    }

    static int integer(String text, String field) {
        try {
            return Integer.parseInt(text.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }
    }

    static double decimal(String text, String field) {
        try {
            return Double.parseDouble(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number, such as 0.05.");
        }
    }

    static List<String> list(String commaSeparated) {
        return Arrays.stream(commaSeparated.split("\\s*,\\s*")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    static String when(Instant instant) {
        return WHEN.format(instant);
    }

    static String km(int km) {
        return String.format(Locale.ROOT, "%,d km", km);
    }
}
