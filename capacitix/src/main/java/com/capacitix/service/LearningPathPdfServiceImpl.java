package com.capacitix.service;

import com.capacitix.dto.LearningPathItem;
import com.capacitix.dto.LearningPathResponse;
import com.capacitix.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class LearningPathPdfServiceImpl
        implements LearningPathPdfService {

    // =========================================================
    // PAGE SETTINGS
    // =========================================================

    private static final float PAGE_WIDTH =
            PDRectangle.A4.getWidth();

    private static final float PAGE_HEIGHT =
            PDRectangle.A4.getHeight();

    private static final float MARGIN = 45;

    private static final float CONTENT_WIDTH =
            PAGE_WIDTH - (MARGIN * 2);


    // =========================================================
    // FONTS
    // =========================================================

    private static final PDType1Font FONT_REGULAR =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
            );

    private static final PDType1Font FONT_BOLD =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD
            );

    private static final PDType1Font FONT_ITALIC =
            new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_OBLIQUE
            );


    // =========================================================
    // MAIN PDF GENERATION
    // =========================================================

    @Override
    public byte[] generateLearningPathPdf(
            User employee,
            LearningPathResponse learningPath
    ) {

        if (employee == null) {

            throw new IllegalArgumentException(
                    "Employee cannot be null."
            );
        }

        if (learningPath == null) {

            throw new IllegalArgumentException(
                    "Learning path cannot be null."
            );
        }

        try (
                PDDocument document = new PDDocument();

                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()
        ) {

            // -------------------------------------------------
            // CREATE FIRST PAGE
            // -------------------------------------------------

            PdfPageState state =
                    createPage(document);


            // -------------------------------------------------
            // DRAW HEADER
            // -------------------------------------------------

            drawHeader(
                    state,
                    employee,
                    learningPath
            );


            // -------------------------------------------------
            // EMPLOYEE INFORMATION
            // -------------------------------------------------

            drawEmployeeInformation(
                    state,
                    employee,
                    learningPath
            );


            // -------------------------------------------------
            // SUMMARY
            // -------------------------------------------------

            drawSummary(
                    state,
                    learningPath
            );


            // -------------------------------------------------
            // AI RECOMMENDATION
            // -------------------------------------------------

            drawAiRecommendation(
                    state,
                    learningPath
            );


            // -------------------------------------------------
            // LEARNING JOURNEY
            // -------------------------------------------------

            drawLearningJourneyHeader(
                    state
            );


            // -------------------------------------------------
            // LEARNING PATH ITEMS
            // -------------------------------------------------

            List<LearningPathItem> items =
                    learningPath.getLearningPath();

            if (items == null || items.isEmpty()) {

                ensureSpace(
                        state,
                        70
                );

                drawText(
                        state,
                        "No personalized learning modules are available yet.",
                        MARGIN,
                        state.y,
                        10,
                        FONT_ITALIC
                );

                state.y -= 35;

            } else {

                for (int i = 0; i < items.size(); i++) {

                    LearningPathItem item =
                            items.get(i);

                    if (item == null) {
                        continue;
                    }

                    drawLearningPathItem(
                            state,
                            item,
                            i + 1
                    );
                }
            }


            // -------------------------------------------------
            // FINAL EXPECTED OUTCOME
            // -------------------------------------------------

            drawExpectedOutcome(
                    state,
                    learningPath
            );


            // -------------------------------------------------
            // FINAL PAGE FOOTER
            // -------------------------------------------------

            drawFooter(
                    state
            );


            // =================================================
            // IMPORTANT PDFBOX FIX
            // =================================================
            //
            // The content stream MUST be closed before
            // PDDocument.save().
            //
            // Otherwise PDFBox throws:
            //
            // Cannot read while there is an open stream writer
            //
            // =================================================

            closeContentStream(
                    state
            );


            // -------------------------------------------------
            // SAVE PDF
            // -------------------------------------------------

            document.save(
                    outputStream
            );


            // -------------------------------------------------
            // RETURN PDF BYTES
            // -------------------------------------------------

            return outputStream.toByteArray();

        } catch (IOException exception) {

            log.error(
                    "Failed to generate learning path PDF.",
                    exception
            );

            throw new IllegalStateException(
                    "Unable to generate learning path PDF.",
                    exception
            );
        }
    }


    // =========================================================
    // CREATE PAGE
    // =========================================================

    private PdfPageState createPage(
            PDDocument document
    ) throws IOException {

        PDPage page =
                new PDPage(
                        PDRectangle.A4
                );

        document.addPage(
                page
        );

        PDPageContentStream contentStream =
                new PDPageContentStream(
                        document,
                        page
                );

        PdfPageState state =
                new PdfPageState();

        state.document =
                document;

        state.page =
                page;

        state.contentStream =
                contentStream;

        state.y =
                PAGE_HEIGHT - MARGIN;

        return state;
    }


    // =========================================================
    // NEW PAGE
    // =========================================================

    private void newPage(
            PdfPageState state
    ) throws IOException {

        // -----------------------------------------------------
        // CLOSE PREVIOUS PAGE STREAM
        // -----------------------------------------------------

        closeContentStream(
                state
        );


        // -----------------------------------------------------
        // CREATE NEW PAGE
        // -----------------------------------------------------

        PDPage page =
                new PDPage(
                        PDRectangle.A4
                );

        state.document.addPage(
                page
        );

        state.page =
                page;


        // -----------------------------------------------------
        // OPEN NEW CONTENT STREAM
        // -----------------------------------------------------

        state.contentStream =
                new PDPageContentStream(
                        state.document,
                        page
                );


        // -----------------------------------------------------
        // RESET Y POSITION
        // -----------------------------------------------------

        state.y =
                PAGE_HEIGHT - MARGIN;
    }


    // =========================================================
    // CLOSE CONTENT STREAM
    // =========================================================

    private void closeContentStream(
            PdfPageState state
    ) throws IOException {

        if (state == null) {
            return;
        }

        if (state.contentStream != null) {

            state.contentStream.close();

            state.contentStream = null;
        }
    }


    // =========================================================
    // HEADER
    // =========================================================

    private void drawHeader(
            PdfPageState state,
            User employee,
            LearningPathResponse learningPath
    ) throws IOException {

        drawText(
                state,
                "CAPACITIX",
                MARGIN,
                state.y,
                22,
                FONT_BOLD
        );

        state.y -= 26;

        drawText(
                state,
                "PERSONALIZED AI LEARNING PATH",
                MARGIN,
                state.y,
                12,
                FONT_BOLD
        );

        state.y -= 18;

        drawLine(
                state,
                MARGIN,
                state.y,
                PAGE_WIDTH - MARGIN,
                state.y
        );

        state.y -= 25;
    }


    // =========================================================
    // EMPLOYEE INFORMATION
    // =========================================================

    private void drawEmployeeInformation(
            PdfPageState state,
            User employee,
            LearningPathResponse learningPath
    ) throws IOException {

        drawSectionTitle(
                state,
                "Employee Information"
        );

        String employeeName =
                safe(
                        learningPath.getEmployeeName(),
                        employee.getFullName(),
                        "Employee"
                );

        String employeeRole =
                safe(
                        learningPath.getEmployeeRole(),
                        "Employee"
                );

        drawLabelValue(
                state,
                "Employee Name",
                employeeName
        );

        drawLabelValue(
                state,
                "Role",
                employeeRole
        );

        if (employee.getDepartment() != null
                && !employee.getDepartment().isBlank()) {

            drawLabelValue(
                    state,
                    "Department",
                    employee.getDepartment()
            );
        }

        state.y -= 12;
    }


    // =========================================================
    // SUMMARY
    // =========================================================

    private void drawSummary(
            PdfPageState state,
            LearningPathResponse learningPath
    ) throws IOException {

        drawSectionTitle(
                state,
                "Skill Gap Summary"
        );

        float boxWidth =
                (CONTENT_WIDTH - 20) / 3;

        float boxHeight = 62;

        ensureSpace(
                state,
                boxHeight + 35
        );

        drawSummaryBox(
                state,
                MARGIN,
                state.y,
                boxWidth,
                boxHeight,
                "TOTAL SKILL GAPS",
                String.valueOf(
                        learningPath.getTotalSkillGaps()
                )
        );

        drawSummaryBox(
                state,
                MARGIN + boxWidth + 10,
                state.y,
                boxWidth,
                boxHeight,
                "HIGH PRIORITY",
                String.valueOf(
                        learningPath.getHighPriorityGaps()
                )
        );

        drawSummaryBox(
                state,
                MARGIN + ((boxWidth + 10) * 2),
                state.y,
                boxWidth,
                boxHeight,
                "MEDIUM PRIORITY",
                String.valueOf(
                        learningPath.getMediumPriorityGaps()
                )
        );

        state.y -= boxHeight + 25;
    }


    // =========================================================
    // AI RECOMMENDATION
    // =========================================================

    private void drawAiRecommendation(
            PdfPageState state,
            LearningPathResponse learningPath
    ) throws IOException {

        String recommendation =
                safe(
                        learningPath.getOverallRecommendation(),
                        "Complete the recommended learning modules to improve identified skill gaps."
                );

        List<String> lines =
                wrapText(
                        recommendation,
                        FONT_REGULAR,
                        10,
                        CONTENT_WIDTH - 24
                );

        float boxHeight =
                Math.max(
                        65,
                        30 + (lines.size() * 14)
                );

        ensureSpace(
                state,
                boxHeight + 30
        );

        drawSectionTitle(
                state,
                "AI Learning Recommendation"
        );

        float top =
                state.y;

        drawBorderBox(
                state,
                MARGIN,
                top - boxHeight,
                CONTENT_WIDTH,
                boxHeight
        );

        float textY =
                top - 25;

        drawText(
                state,
                "AI Recommendation",
                MARGIN + 12,
                textY,
                10,
                FONT_BOLD
        );

        textY -= 17;

        for (String line : lines) {

            drawText(
                    state,
                    line,
                    MARGIN + 12,
                    textY,
                    9,
                    FONT_REGULAR
            );

            textY -= 14;
        }

        state.y =
                top - boxHeight - 22;
    }


    // =========================================================
    // LEARNING JOURNEY HEADER
    // =========================================================

    private void drawLearningJourneyHeader(
            PdfPageState state
    ) throws IOException {

        ensureSpace(
                state,
                70
        );

        drawSectionTitle(
                state,
                "Personalized Learning Journey"
        );

        drawText(
                state,
                "Skill Gap -> AI Recommendation -> Training -> Practical Application -> Improved Competency",
                MARGIN,
                state.y,
                8,
                FONT_ITALIC
        );

        state.y -= 25;
    }


    // =========================================================
    // LEARNING PATH ITEM
    // =========================================================

    private void drawLearningPathItem(
            PdfPageState state,
            LearningPathItem item,
            int number
    ) throws IOException {

        if (item == null) {
            return;
        }

        String skillName =
                safe(
                        item.getSkillName(),
                        "Skill"
                );

        String priority =
                safe(
                        item.getPriority(),
                        "MEDIUM"
                );

        String currentScore =
                item.getCurrentScore() != null
                        ? String.format(
                                "%.1f%%",
                                item.getCurrentScore()
                        )
                        : "Not assessed";

        String gapReason =
                safe(
                        item.getGapReason(),
                        "Recommended based on the identified skill gap."
                );

        String practicalTask =
                safe(
                        item.getPracticalTask(),
                        "Complete practical exercises using this skill."
                );

        String estimatedDuration =
                safe(
                        item.getEstimatedDuration(),
                        "Not specified"
                );

        String expectedOutcome =
                safe(
                        item.getExpectedOutcome(),
                        "Improve practical understanding and application of this skill."
                );

        Integer learningOrder =
                item.getLearningOrder();

        String learningOrderText =
                learningOrder != null
                        ? String.valueOf(
                                learningOrder
                        )
                        : String.valueOf(
                                number
                        );


        // -----------------------------------------------------
        // WRAP TEXT
        // -----------------------------------------------------

        List<String> reasonLines =
                wrapText(
                        gapReason,
                        FONT_REGULAR,
                        8,
                        CONTENT_WIDTH - 28
                );

        List<String> practicalTaskLines =
                wrapText(
                        practicalTask,
                        FONT_REGULAR,
                        8,
                        CONTENT_WIDTH - 28
                );

        List<String> outcomeLines =
                wrapText(
                        expectedOutcome,
                        FONT_REGULAR,
                        8,
                        CONTENT_WIDTH - 28
                );


        // -----------------------------------------------------
        // TOPICS
        // -----------------------------------------------------

        List<String> topicLines =
                new ArrayList<>();

        if (item.getTopics() != null
                && !item.getTopics().isEmpty()) {

            for (String topic : item.getTopics()) {

                if (topic == null
                        || topic.isBlank()) {

                    continue;
                }

                topicLines.add(
                        "- " + cleanText(topic)
                );
            }
        }

        if (topicLines.isEmpty()) {

            topicLines.add(
                    "- Follow the recommended fundamentals and practical concepts."
            );
        }


        // -----------------------------------------------------
        // CARD HEIGHT
        // -----------------------------------------------------

        float cardHeight =
                180
                        + (reasonLines.size() * 12)
                        + (topicLines.size() * 11)
                        + (practicalTaskLines.size() * 12)
                        + (outcomeLines.size() * 12);

        float maxCardHeight =
                PAGE_HEIGHT - (MARGIN * 2);

        cardHeight =
                Math.min(
                        cardHeight,
                        maxCardHeight
                );


        // -----------------------------------------------------
        // PAGE SPACE
        // -----------------------------------------------------

        ensureSpace(
                state,
                Math.min(
                        cardHeight,
                        maxCardHeight
                )
        );


        float cardTop =
                state.y;


        // -----------------------------------------------------
        // CARD
        // -----------------------------------------------------

        drawBorderBox(
                state,
                MARGIN,
                cardTop - cardHeight,
                CONTENT_WIDTH,
                cardHeight
        );


        // -----------------------------------------------------
        // ITEM NUMBER
        // -----------------------------------------------------

        drawText(
                state,
                String.format(
                        "%02d",
                        number
                ),
                MARGIN + 14,
                cardTop - 22,
                14,
                FONT_BOLD
        );


        // -----------------------------------------------------
        // SKILL NAME
        // -----------------------------------------------------

        drawText(
                state,
                cleanText(skillName),
                MARGIN + 48,
                cardTop - 20,
                13,
                FONT_BOLD
        );


        // -----------------------------------------------------
        // PRIORITY + ORDER
        // -----------------------------------------------------

        drawText(
                state,
                "Priority: " + cleanText(priority),
                MARGIN + 48,
                cardTop - 38,
                8,
                FONT_BOLD
        );

        drawText(
                state,
                "Learning Order: "
                        + learningOrderText,
                MARGIN + 155,
                cardTop - 38,
                8,
                FONT_REGULAR
        );


        // -----------------------------------------------------
        // CURRENT SCORE
        // -----------------------------------------------------

        float contentY =
                cardTop - 60;

        drawText(
                state,
                "Current Assessment Score: "
                        + currentScore,
                MARGIN + 14,
                contentY,
                8,
                FONT_BOLD
        );

        contentY -= 22;


        // -----------------------------------------------------
        // WHY THIS SKILL
        // -----------------------------------------------------

        drawText(
                state,
                "Why This Skill Is Recommended",
                MARGIN + 14,
                contentY,
                9,
                FONT_BOLD
        );

        contentY -= 13;

        for (String line : reasonLines) {

            drawText(
                    state,
                    line,
                    MARGIN + 14,
                    contentY,
                    8,
                    FONT_REGULAR
            );

            contentY -= 12;
        }

        contentY -= 5;


        // -----------------------------------------------------
        // TOPICS
        // -----------------------------------------------------

        drawText(
                state,
                "Topics To Learn",
                MARGIN + 14,
                contentY,
                9,
                FONT_BOLD
        );

        contentY -= 13;

        for (String topic : topicLines) {

            drawText(
                    state,
                    topic,
                    MARGIN + 14,
                    contentY,
                    8,
                    FONT_REGULAR
            );

            contentY -= 11;
        }

        contentY -= 5;


        // -----------------------------------------------------
        // PRACTICAL TASK
        // -----------------------------------------------------

        drawText(
                state,
                "Practical Task",
                MARGIN + 14,
                contentY,
                9,
                FONT_BOLD
        );

        contentY -= 13;

        for (String line : practicalTaskLines) {

            drawText(
                    state,
                    line,
                    MARGIN + 14,
                    contentY,
                    8,
                    FONT_REGULAR
            );

            contentY -= 12;
        }

        contentY -= 5;


        // -----------------------------------------------------
        // DURATION
        // -----------------------------------------------------

        drawText(
                state,
                "Estimated Duration: "
                        + cleanText(estimatedDuration),
                MARGIN + 14,
                contentY,
                8,
                FONT_BOLD
        );

        contentY -= 18;


        // -----------------------------------------------------
        // EXPECTED OUTCOME
        // -----------------------------------------------------

        drawText(
                state,
                "Expected Outcome",
                MARGIN + 14,
                contentY,
                9,
                FONT_BOLD
        );

        contentY -= 13;

        for (String line : outcomeLines) {

            drawText(
                    state,
                    line,
                    MARGIN + 14,
                    contentY,
                    8,
                    FONT_REGULAR
            );

            contentY -= 12;
        }


        // -----------------------------------------------------
        // MOVE DOWN
        // -----------------------------------------------------

        state.y =
                cardTop - cardHeight - 18;
    }


    // =========================================================
    // EXPECTED OUTCOME
    // =========================================================

    private void drawExpectedOutcome(
            PdfPageState state,
            LearningPathResponse learningPath
    ) throws IOException {

        String outcome =
                safe(
                        learningPath.getExpectedOutcome(),
                        "Improved competency after completing the personalized learning path."
                );

        List<String> lines =
                wrapText(
                        outcome,
                        FONT_REGULAR,
                        10,
                        CONTENT_WIDTH - 24
                );

        float boxHeight =
                Math.max(
                        65,
                        32 + (lines.size() * 14)
                );

        ensureSpace(
                state,
                boxHeight + 40
        );

        drawSectionTitle(
                state,
                "Expected Outcome"
        );

        float top =
                state.y;

        drawBorderBox(
                state,
                MARGIN,
                top - boxHeight,
                CONTENT_WIDTH,
                boxHeight
        );

        float textY =
                top - 25;

        for (String line : lines) {

            drawText(
                    state,
                    line,
                    MARGIN + 12,
                    textY,
                    9,
                    FONT_REGULAR
            );

            textY -= 14;
        }

        state.y =
                top - boxHeight - 20;
    }


    // =========================================================
    // SECTION TITLE
    // =========================================================

    private void drawSectionTitle(
            PdfPageState state,
            String title
    ) throws IOException {

        ensureSpace(
                state,
                50
        );

        drawText(
                state,
                title,
                MARGIN,
                state.y,
                12,
                FONT_BOLD
        );

        state.y -= 20;
    }


    // =========================================================
    // LABEL + VALUE
    // =========================================================

    private void drawLabelValue(
            PdfPageState state,
            String label,
            String value
    ) throws IOException {

        drawText(
                state,
                label + ":",
                MARGIN,
                state.y,
                9,
                FONT_BOLD
        );

        drawText(
                state,
                cleanText(value),
                MARGIN + 120,
                state.y,
                9,
                FONT_REGULAR
        );

        state.y -= 15;
    }


    // =========================================================
    // SUMMARY BOX
    // =========================================================

    private void drawSummaryBox(
            PdfPageState state,
            float x,
            float y,
            float width,
            float height,
            String title,
            String value
    ) throws IOException {

        drawBorderBox(
                state,
                x,
                y - height,
                width,
                height
        );

        drawText(
                state,
                title,
                x + 10,
                y - 19,
                7,
                FONT_BOLD
        );

        drawText(
                state,
                value,
                x + 10,
                y - 43,
                18,
                FONT_BOLD
        );
    }


    // =========================================================
    // PROGRESS BAR
    // =========================================================

    private void drawProgressBar(
            PdfPageState state,
            float x,
            float y,
            float width,
            float height,
            int progress
    ) throws IOException {

        drawRectangle(
                state,
                x,
                y,
                width,
                height
        );

        if (progress > 0) {

            float progressWidth =
                    width * (progress / 100f);

            drawRectangle(
                    state,
                    x,
                    y,
                    progressWidth,
                    height
            );
        }
    }


    // =========================================================
    // BORDER BOX
    // =========================================================

    private void drawBorderBox(
            PdfPageState state,
            float x,
            float y,
            float width,
            float height
    ) throws IOException {

        state.contentStream.setLineWidth(
                0.7f
        );

        state.contentStream.addRect(
                x,
                y,
                width,
                height
        );

        state.contentStream.stroke();
    }


    // =========================================================
    // RECTANGLE
    // =========================================================

    private void drawRectangle(
            PdfPageState state,
            float x,
            float y,
            float width,
            float height
    ) throws IOException {

        state.contentStream.addRect(
                x,
                y,
                width,
                height
        );

        state.contentStream.fill();
    }


    // =========================================================
    // LINE
    // =========================================================

    private void drawLine(
            PdfPageState state,
            float x1,
            float y1,
            float x2,
            float y2
    ) throws IOException {

        state.contentStream.setLineWidth(
                0.7f
        );

        state.contentStream.moveTo(
                x1,
                y1
        );

        state.contentStream.lineTo(
                x2,
                y2
        );

        state.contentStream.stroke();
    }


    // =========================================================
    // TEXT
    // =========================================================

    private void drawText(
            PdfPageState state,
            String text,
            float x,
            float y,
            float fontSize,
            PDType1Font font
    ) throws IOException {

        if (text == null || text.isBlank()) {
            return;
        }

        String safeText =
                cleanText(text);

        state.contentStream.beginText();

        state.contentStream.setFont(
                font,
                fontSize
        );

        state.contentStream.newLineAtOffset(
                x,
                y
        );

        state.contentStream.showText(
                safeText
        );

        state.contentStream.endText();
    }


    // =========================================================
    // TEXT WRAPPING
    // =========================================================

    private List<String> wrapText(
            String text,
            PDType1Font font,
            float fontSize,
            float maxWidth
    ) throws IOException {

        String safeText =
                cleanText(text);

        if (safeText.isBlank()) {

            return List.of("");
        }

        String[] words =
                safeText.split("\\s+");

        ArrayList<String> lines =
                new ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        for (String word : words) {

            String candidate =
                    current.isEmpty()
                            ? word
                            : current + " " + word;

            float width =
                    font.getStringWidth(candidate)
                            / 1000f
                            * fontSize;

            if (width <= maxWidth) {

                current =
                        new StringBuilder(
                                candidate
                        );

            } else {

                if (!current.isEmpty()) {

                    lines.add(
                            current.toString()
                    );
                }

                current =
                        new StringBuilder(
                                word
                        );
            }
        }

        if (!current.isEmpty()) {

            lines.add(
                    current.toString()
            );
        }

        return lines;
    }


    // =========================================================
    // PAGE SPACE
    // =========================================================

    private void ensureSpace(
            PdfPageState state,
            float requiredHeight
    ) throws IOException {

        if (state.y - requiredHeight
                < MARGIN + 35) {

            // Footer is drawn while the current
            // content stream is still open.

            drawFooter(
                    state
            );

            // newPage() closes the current stream
            // before creating the next one.

            newPage(
                    state
            );
        }
    }


    // =========================================================
    // FOOTER
    // =========================================================

    private void drawFooter(
            PdfPageState state
    ) throws IOException {

        float footerY = 25;

        drawLine(
                state,
                MARGIN,
                footerY + 12,
                PAGE_WIDTH - MARGIN,
                footerY + 12
        );

        drawText(
                state,
                "Capacitix - Personalized AI Learning System",
                MARGIN,
                footerY,
                7,
                FONT_ITALIC
        );

        drawText(
                state,
                "Generated Learning Path",
                PAGE_WIDTH - MARGIN - 105,
                footerY,
                7,
                FONT_ITALIC
        );
    }


    // =========================================================
    // SAFE STRING
    // =========================================================

    private String safe(
            String value,
            String fallback
    ) {

        if (value == null
                || value.isBlank()) {

            return fallback;
        }

        return value;
    }


    private String safe(
            String value,
            String fallback1,
            String fallback2
    ) {

        if (value != null
                && !value.isBlank()) {

            return value;
        }

        if (fallback1 != null
                && !fallback1.isBlank()) {

            return fallback1;
        }

        return fallback2;
    }


    // =========================================================
    // PDF SAFE TEXT
    // =========================================================

    private String cleanText(
            String text
    ) {

        if (text == null) {

            return "";
        }

        /*
         * Helvetica is a standard PDF font and does not support
         * every Unicode character.
         *
         * Convert common Unicode characters to ASCII equivalents.
         */

        return text
                .replace("→", "->")
                .replace("←", "<-")
                .replace("•", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("’", "'")
                .replace("‘", "'")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("₹", "INR")
                .replaceAll(
                        "[^\\x20-\\x7E\\n\\r\\t]",
                        ""
                );
    }


    // =========================================================
    // PAGE STATE
    // =========================================================

    private static class PdfPageState {

        private PDDocument document;

        private PDPage page;

        private PDPageContentStream contentStream;

        private float y;
    }
}