package com.dcb.service;

import com.dcb.entity.TransactionStatusEntity;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Account statements and transaction receipts as PDF. */
@Service
public class PdfService {

    private static final Color BRAND = new Color(10, 58, 72);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
    private static final Font SUB = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

    public byte[] statement(String holderName, String accountNo, LocalDate from, LocalDate to,
                            BigDecimal currentBalance, List<TransactionStatusEntity> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.open();
        header(doc, "Account Statement");
        doc.add(new Paragraph("Account holder: " + holderName + "    Account: " + accountNo, VALUE));
        doc.add(new Paragraph("Period: " + from + " to " + to + "    Current balance: INR " + currentBalance.toPlainString(), VALUE));
        doc.add(new Paragraph(" "));

        String[] headers = {"Date", "Transaction ID", "Reference", "Type", "Description", "Debit", "Credit", "Balance", "Status"};
        PdfPTable table = new PdfPTable(new float[]{11, 15, 15, 9, 22, 8, 8, 9, 7});
        table.setWidthPercentage(100);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEAD));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(4);
            table.addCell(cell);
        }
        if (entries.isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase("No transactions in this period.", CELL));
            cell.setColspan(headers.length);
            cell.setPadding(6);
            table.addCell(cell);
        }
        for (TransactionStatusEntity t : entries) {
            boolean debit = t.getDirection().name().equals("DEBIT");
            add(table, t.getCreatedAt().format(TS));
            add(table, t.getTransactionId());
            add(table, t.getReference());
            add(table, t.getType().name());
            add(table, t.getDescription());
            add(table, debit ? t.getTransactionAmount().toPlainString() : "");
            add(table, debit ? "" : t.getTransactionAmount().toPlainString());
            add(table, t.getBalanceAfter() == null ? "" : t.getBalanceAfter().toPlainString());
            add(table, t.getStatus().name());
        }
        doc.add(table);
        footer(doc);
        doc.close();
        return out.toByteArray();
    }

    public byte[] receipt(String holderName, TransactionStatusEntity t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 30, 30, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.open();
        header(doc, "Transaction Receipt");

        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("Account holder", holderName);
        rows.put("Account", t.getAccountNo());
        rows.put("Transaction ID", t.getTransactionId());
        rows.put("Reference", t.getReference());
        rows.put("Type", t.getType().name() + " (" + t.getDirection().name() + ")");
        rows.put("Amount", "INR " + t.getTransactionAmount().toPlainString());
        rows.put("Balance before", t.getBalanceBefore() == null ? "-" : "INR " + t.getBalanceBefore().toPlainString());
        rows.put("Balance after", t.getBalanceAfter() == null ? "-" : "INR " + t.getBalanceAfter().toPlainString());
        rows.put("Status", t.getStatus().name());
        rows.put("Description", t.getDescription() == null ? "-" : t.getDescription());
        rows.put("RRN", t.getRrn() == null ? "-" : t.getRrn());
        rows.put("STAN", t.getStan() == null ? "-" : String.valueOf(t.getStan()));
        rows.put("Date & time", t.getCreatedAt().format(TS));

        PdfPTable table = new PdfPTable(new float[]{35, 65});
        table.setWidthPercentage(100);
        rows.forEach((k, v) -> {
            PdfPCell key = new PdfPCell(new Phrase(k, LABEL));
            key.setBorder(Rectangle.BOTTOM);
            key.setPadding(5);
            PdfPCell val = new PdfPCell(new Phrase(v, VALUE));
            val.setBorder(Rectangle.BOTTOM);
            val.setPadding(5);
            table.addCell(key);
            table.addCell(val);
        });
        doc.add(table);
        footer(doc);
        doc.close();
        return out.toByteArray();
    }

    private void header(Document doc, String title) {
        doc.add(new Paragraph("DIU CITY BANK", TITLE));
        doc.add(new Paragraph(title + "  |  Generated " + java.time.LocalDateTime.now().format(TS), SUB));
        doc.add(new Paragraph(" "));
    }

    private void footer(Document doc) {
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("DEMO / SIMULATION - academic project. No real money was moved.", SUB));
    }

    private void add(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, CELL));
        cell.setPadding(3);
        table.addCell(cell);
    }
}
