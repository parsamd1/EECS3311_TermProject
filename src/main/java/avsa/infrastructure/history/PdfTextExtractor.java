package avsa.infrastructure.history;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Adapter pattern adaptee: wraps the third-party PDFBox library behind one small method. */
public class PdfTextExtractor {

    public String extractText(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new HistoryFileException("The PDF file could not be read: " + file.getFileName(), e);
        }
    }
}
