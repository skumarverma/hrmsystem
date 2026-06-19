package com.hrm.hrmsystem.util;

import com.hrm.hrmsystem.entity.Payslip;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.layout.element.Image;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class PdfGeneratorUtil {

    private static final String PDF_DIRECTORY = "payslips/";
    private static final String COMPANY_NAME = "ENEGO STARTUP ADVISORY PRIVATE LIMITED";
    private static final String COMPANY_ADDRESS = "Unit No.712, 7th Floor Tower-C Iconic Corenthum Noida Sector-62A -201301";

    /**
     * Generate PDF for payslip
     */
    public String generatePayslipPdf(Payslip payslip) throws IOException {
        try {
            // Create directory if not exists
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(PDF_DIRECTORY));

            // Generate unique filename
            String fileName = generateFileName(payslip);
            String filePath = PDF_DIRECTORY + fileName;

            // Create PDF document
            PdfWriter writer = new PdfWriter(filePath);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);
            
            // Create fonts dynamically per document to avoid cross-document object reference errors
            PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontNormal = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            
            // Set margins to center the 540f table exactly on the 595f A4 page width (27.5f margins left/right)
            document.setMargins(60, 27.5f, 20, 27.5f);

            // Unified 7-column layout:
            // Col 1 = 20f (Left blank column spacer)
            // Col 2 = 140f (Earnings / Employee Name)
            // Col 3 = 80f (Gross / Spacer)
            // Col 4 = 80f (Earning / Employee Code)
            // Col 5 = 120f (Deductions Type / Joining Date)
            // Col 6 = 80f (Deductions Amt / Leaves Taken / LWP / Paid Days)
            // Col 7 = 20f (Right blank column spacer)
            // Total width = 20 + 140 + 80 + 80 + 120 + 80 + 20 = 540f
            Table table = new Table(new float[]{ 20f, 130f, 85f, 85f, 120f, 80f, 20f });
            table.setWidth(540f);

            DeviceRgb lightBlue = new DeviceRgb(220, 230, 242);
            DeviceRgb lightGray = new DeviceRgb(217, 217, 217);
            DeviceRgb darkGray = new DeviceRgb(173, 170, 171);

            // 1. Header (Company Name & Logo) - Spans all 7 columns
            Cell nameCell = new Cell(1, 5).add(new Paragraph(COMPANY_NAME)
                    .setFontSize(9)
                    .setFont(fontBold)
                    .setUnderline()
                    .setTextAlignment(TextAlignment.CENTER))
                    .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                    .setPadding(5f)
                    .setPaddingLeft(75f);

            Cell logoCell = new Cell(1, 2)
                    .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                    .setPadding(0f); // Ensures edge-to-edge constraint bounds

            try (java.io.InputStream is = this.getClass().getClassLoader().getResourceAsStream("logo.png")) {
                if (is != null) {
                    byte[] imageBytes = is.readAllBytes();
                    ImageData imageData = ImageDataFactory.create(imageBytes);
                    Image img = new Image(imageData);
                    
                    // 1. Lock absolute physical bounds to stop vertical row expansion
                    img.setWidth(100f);  // Fills column 6 (80f) + column 7 (20f) exactly
                    img.setHeight(32f);  // Explicitly limits height
                    
                    logoCell.add(img);
                } else {
                    logoCell.add(new Paragraph("ENEGO")
                            .setFontSize(14)
                            .setFont(fontBold)
                            .setTextAlignment(TextAlignment.CENTER));
                }
            } catch (Exception e) {
                logoCell.add(new Paragraph("ENEGO")
                        .setFontSize(14)
                        .setFont(fontBold)
                        .setTextAlignment(TextAlignment.CENTER));
            }

            table.addCell(nameCell);
            table.addCell(logoCell);

            // 2. Company Address - Spans all 7 columns
            table.addCell(new Cell(1, 7).add(new Paragraph(COMPANY_ADDRESS)
                    .setFontSize(9)
                    .setFont(fontBold)
                    .setTextAlignment(TextAlignment.CENTER))
                    .setPadding(0.5f));

            // 3. Payslip Month Title - Spans all 7 columns
            String formattedMonth = formatMonthYearMMM(payslip.getMonthYear());
            table.addCell(new Cell(1, 7).add(new Paragraph("Pay Slip of the Month of " + formattedMonth)
                    .setFontSize(9)
                    .setFont(fontBold)
                    .setTextAlignment(TextAlignment.CENTER))
                    .setPadding(0.5f));

            // 4. Employee Information Grid (With left and right spacer columns)
            double unpaidDays = payslip.getUnpaidLeaveDays() != null ? payslip.getUnpaidLeaveDays() : 0.0;
            double absentDays = payslip.getAbsentDays() != null ? payslip.getAbsentDays() : 0.0;
            
            // Calculate Sundays in the month YearMonth up to today (if current month)
            int sundays = 0;
            try {
                YearMonth ym = YearMonth.parse(payslip.getMonthYear());
                LocalDate today = LocalDate.now();
                int limitDay = ym.lengthOfMonth();
                
                // If it is the current month and current year, only count up to today
                if (ym.getYear() == today.getYear() && ym.getMonthValue() == today.getMonthValue()) {
                    limitDay = today.getDayOfMonth();
                }
                
                for (int d = 1; d <= limitDay; d++) {
                    if (ym.atDay(d).getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                        sundays++;
                    }
                }
            } catch (Exception e) {
                sundays = 0;
            }

            double presentDays = payslip.getPresentDays() != null ? payslip.getPresentDays() : 0.0;
            double paidLeaves = payslip.getPaidLeaveDays() != null ? payslip.getPaidLeaveDays() : 0.0;
            
            // Get actual number of days in the month for paid days display
            int daysInMonth = 30;
            try {
                String my = payslip.getMonthYear();
                YearMonth ym;
                if (my != null && my.contains("-")) {
                    ym = YearMonth.parse(my);
                } else if (my != null && my.contains(" ")) {
                    String[] parts = my.split(" ");
                    int m = getMonthNumber(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    ym = YearMonth.of(y, m);
                } else {
                    ym = YearMonth.now();
                }
                daysInMonth = ym.lengthOfMonth();
            } catch (Exception e) {
                daysInMonth = 30;
            }
            
            // paidDays = daysInMonth - unpaidDays
            double paidDays = daysInMonth - unpaidDays;
            if (paidDays < 0) {
                paidDays = 0.0;
            }

            String dojStr = "N/A";
            if (payslip.getEmployee().getJoiningDate() != null) {
                dojStr = payslip.getEmployee().getJoiningDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }

            // Row 1 Headers
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph("EMPLOYEE NAME").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setBackgroundColor(lightBlue).setPadding(0.5f)); // Col 3 Spacer
            table.addCell(new Cell(1, 1).add(new Paragraph("EMPLOYEE CODE").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("DATE OF JOINING").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("LEAVES TAKEN").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            addRightSpacer(table);

            // Row 1 Data
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph(payslip.getEmployee().getFirstName() + " " + payslip.getEmployee().getLastName()).setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(payslip.getEmployee().getEmployeeCode() != null ? payslip.getEmployee().getEmployeeCode() : "N/A").setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(dojStr).setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(String.format("%.2f", payslip.getLeaveDays() != null ? payslip.getLeaveDays() : 0.0)).setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            addRightSpacer(table);

            // Row 2 Headers
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph("DESIGNATION").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f)); // Col 3 Spacer (no color)
            table.addCell(new Cell(1, 1).setBackgroundColor(lightBlue).setPadding(0.5f)); // Col 4 Spacer (light blue)
            table.addCell(new Cell(1, 1).setBackgroundColor(lightBlue).setPadding(0.5f)); // Col 5 Spacer (light blue)
            table.addCell(new Cell(1, 1).add(new Paragraph("LWP DAYS").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            addRightSpacer(table);

            // Row 2 Data
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph(payslip.getEmployee().getDesignation() != null ? payslip.getEmployee().getDesignation() : "N/A").setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(String.format("%.2f", unpaidDays)).setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            addRightSpacer(table);

            // Row 3 Headers
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph("DEPARTMENT").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f)); // Col 3 Spacer (no color)
            table.addCell(new Cell(1, 1).add(new Paragraph("UAN NO.").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setBackgroundColor(lightBlue).setPadding(0.5f)); // Col 5 Spacer (light blue)
            table.addCell(new Cell(1, 1).add(new Paragraph("PAID DAYS").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightBlue).setPadding(0.5f));
            addRightSpacer(table);

            // Row 3 Data
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph(payslip.getEmployee().getDepartment() != null ? payslip.getEmployee().getDepartment().getName() : "N/A").setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f));
            String uanNo = payslip.getEmployee().getUanNo();
            table.addCell(new Cell(1, 1).add(new Paragraph(uanNo != null && !uanNo.trim().isEmpty() ? uanNo : "N/A").setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(String.format("%.2f", paidDays)).setFont(fontNormal).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(0.5f));
            addRightSpacer(table);

            // 5. Salary Details Grid Headers
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph("EARNINGS").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("GROSS").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("EARNING").setFont(fontBold).setFontSize(8).setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(darkGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("DEDUCTIONS TYPE").setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(lightGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("DEDUCTIONS AMT").setFont(fontBold).setFontSize(8).setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(darkGray).setPadding(0.5f));
            addRightSpacer(table);

            // Component values
            BigDecimal basicGross = payslip.getBasicSalary() != null ? payslip.getBasicSalary() : BigDecimal.ZERO;
            BigDecimal hraGross = payslip.getHra() != null ? payslip.getHra() : BigDecimal.ZERO;
            BigDecimal specialGross = payslip.getSpecialAllowance() != null ? payslip.getSpecialAllowance() : BigDecimal.ZERO;
            BigDecimal bonusGross = payslip.getBonus() != null ? payslip.getBonus() : BigDecimal.ZERO;
            BigDecimal incentiveGross = payslip.getIncentive() != null ? payslip.getIncentive() : BigDecimal.ZERO;
            BigDecimal otherGross = payslip.getOtherAllowance() != null ? payslip.getOtherAllowance() : BigDecimal.ZERO;
            BigDecimal totalGross = basicGross.add(hraGross).add(specialGross).add(bonusGross).add(incentiveGross).add(otherGross);

            BigDecimal unpaidDed = payslip.getUnpaidLeaveDeduction() != null ? payslip.getUnpaidLeaveDeduction() : BigDecimal.ZERO;
            BigDecimal absentDed = payslip.getAbsentLeaveDeduction() != null ? payslip.getAbsentLeaveDeduction() : BigDecimal.ZERO;
            // ✅ FIX: absentDed is DISPLAY ONLY — not included in actual deduction total
            BigDecimal totalAttDed = unpaidDed;

            BigDecimal basicEarned = basicGross;
            BigDecimal hraEarned = hraGross;
            BigDecimal specialEarned = specialGross;
            BigDecimal bonusEarned = bonusGross;
            BigDecimal incentiveEarned = incentiveGross;
            BigDecimal otherEarned = otherGross;

            BigDecimal pf = payslip.getPf() != null ? payslip.getPf() : BigDecimal.ZERO;
            BigDecimal esic = payslip.getEsic() != null ? payslip.getEsic() : BigDecimal.ZERO;
            BigDecimal pt = payslip.getProfessionalTax() != null ? payslip.getProfessionalTax() : BigDecimal.ZERO;
            // Use incomeTax for TDS since it maps from Payroll's tax field
            BigDecimal tds = payslip.getIncomeTax() != null ? payslip.getIncomeTax() : (payslip.getTds() != null ? payslip.getTds() : BigDecimal.ZERO);
            BigDecimal loan = payslip.getLoanDeduction() != null ? payslip.getLoanDeduction() : BigDecimal.ZERO;
            BigDecimal lwf = payslip.getLwf() != null ? payslip.getLwf() : BigDecimal.ZERO;
            BigDecimal insurance = BigDecimal.ZERO; // Hidden from payslip as per user request
            
            BigDecimal totalEarned = basicEarned.add(hraEarned).add(specialEarned).add(bonusEarned).add(incentiveEarned).add(otherEarned);
            // Include absentDed in totalDeduction so the PDF displays correct totals
            BigDecimal totalDeduction = pf.add(esic).add(pt).add(tds).add(loan).add(lwf).add(insurance).add(unpaidDed).add(absentDed);
            BigDecimal netSalary = totalEarned.subtract(totalDeduction);
            if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
                netSalary = BigDecimal.ZERO;
            }

            // Add rows
            addSalaryRow(table, "BASIC SALARY", basicGross, basicEarned, "PF", pf);
            addSalaryRow(table, "HRA", hraGross, hraEarned, "ESIC", esic);
            addSalaryRow(table, "SPECIAL ALLOWANCE", specialGross, specialEarned, "PROF TAX", pt);
            addSalaryRow(table, "BONUS", bonusGross, bonusEarned, "TDS", tds);
            addSalaryRow(table, "INCENTIVE", incentiveGross, incentiveEarned, "LOAN DEDUCTION/OTHER", loan);
            addSalaryRow(table, "OTHER ALLOWANCE", otherGross, otherEarned, "LWF", lwf);
            
            // Blank row of the exact same height/padding/borders as normal salary rows
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph(" ").setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f));
            table.addCell(new Cell(1, 1).add(new Paragraph(" ").setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f).setPaddingRight(2f));
            table.addCell(new Cell(1, 1).add(new Paragraph(" ").setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f).setPaddingRight(2f));
            table.addCell(new Cell(1, 1).add(new Paragraph(" ").setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f));
            table.addCell(new Cell(1, 1).add(new Paragraph(" ").setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f));
            addRightSpacer(table);

            // Removed INSURANCE, LWP DEDUCTION, and ABSENT DEDUCTION rows from display as per request
            // but kept their values in the total calculation to ensure correct Net Pay.
            
            // Totals Row
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph("TOTAL").setFont(fontBold).setFontSize(8)).setBackgroundColor(lightGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(formatAmount(totalGross)).setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(lightGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(formatAmount(totalEarned)).setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(darkGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("").setFont(fontBold).setFontSize(8)).setBackgroundColor(lightGray).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph(formatAmount(totalDeduction)).setFont(fontBold).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(darkGray).setPadding(0.5f));
            addRightSpacer(table);

            // Blank Row
            addLeftSpacer(table);
            table.addCell(new Cell(1, 1).add(new Paragraph("").setFontSize(8)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("").setFontSize(8)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("").setFontSize(8)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("").setFontSize(8)).setPadding(0.5f));
            table.addCell(new Cell(1, 1).add(new Paragraph("").setFontSize(8)).setPadding(0.5f));
            addRightSpacer(table);

            // 6. Net Pay Summary Row
            addLeftSpacer(table);
            table.addCell(new Cell(1, 4).add(new Paragraph("Net Pay for the month ( Total Earnings - Total Deductions)")
                    .setFont(fontBold)
                    .setFontSize(8))
                    .setBackgroundColor(lightGray)
                    .setPadding(1));
            table.addCell(new Cell(1, 1).add(new Paragraph(formatAmount(netSalary))
                    .setFont(fontBold)
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT))
                    .setBackgroundColor(darkGray)
                    .setPadding(1));
            addRightSpacer(table);

            // 7. Disclaimer Row - Spans columns 2-5 (span 4) with blank column (span 1) and spacers preserved
            addLeftSpacer(table);
            table.addCell(new Cell(1, 4).add(new Paragraph("(Please Note: This is system generated documents hence no need to authenticate.)")
                    .setFont(fontBold)
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.LEFT))
                    .setPadding(1));
            table.addCell(new Cell(1, 1).setPadding(1));
            addRightSpacer(table);

            // 8. Bottom Page-Framing Blank Row - 2 columns inside the spacers (left spans 4, right spans 1)
            // Left spacer (Col 1)
            table.addCell(new Cell(1, 1).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderLeft(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setHeight(7f));
            
            // Left content block (Col 2-6) - user manual change (spans 5 columns)
            table.addCell(new Cell(1, 5).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderRight(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setHeight(7f));
            
            // Right spacer (Col 7)
            table.addCell(new Cell(1, 1).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderRight(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f))
                    .setHeight(7f));

            document.add(table);
            document.close();

            return filePath;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error generating PDF: " + e.getMessage(), e);
        }
    }

    private void addLeftSpacer(Table table) {
        table.addCell(new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderLeft(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f)));
    }

    private void addRightSpacer(Table table) {
        table.addCell(new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setBorderRight(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.5f)));
    }

    private void addSalaryRow(Table table, String earnType, BigDecimal gross, BigDecimal earned, String deductType, BigDecimal deductAmt) {
        addLeftSpacer(table);
        table.addCell(new Cell(1, 1).add(new Paragraph(earnType).setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f));
        table.addCell(new Cell(1, 1).add(new Paragraph(gross.compareTo(BigDecimal.ZERO) >= 0 ? formatAmount(gross) : "").setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(0.5f).setPaddingLeft(2f).setPaddingRight(2f));
        table.addCell(new Cell(1, 1).add(new Paragraph(earned.compareTo(BigDecimal.ZERO) >= 0 ? formatAmount(earned) : "").setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(0.5f).setPaddingLeft(2f).setPaddingRight(2f));
        table.addCell(new Cell(1, 1).add(new Paragraph(deductType).setFontSize(8)).setPadding(0.5f).setPaddingLeft(2f));
        table.addCell(new Cell(1, 1).add(new Paragraph(deductAmt.compareTo(BigDecimal.ZERO) >= 0 ? formatAmount(deductAmt) : "").setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(0.5f).setPaddingLeft(2f));
        addRightSpacer(table);
    }

    private BigDecimal getEarned(BigDecimal gross, double prorationFactor) {
        if (gross == null) return BigDecimal.ZERO;
        return gross.multiply(BigDecimal.valueOf(prorationFactor)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String generateFileName(Payslip payslip) {
        String empId = payslip.getEmployee().getId().toString();
        String monthYear = payslip.getMonthYear();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("Payslip_%s_%s_%s.pdf", empId, monthYear, uuid);
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return String.format("%.2f", amount);
    }

    private String formatMonthYearMMM(String monthYear) {
        try {
            YearMonth ym = YearMonth.parse(monthYear);
            return ym.format(DateTimeFormatter.ofPattern("MMM-yy")).toUpperCase();
        } catch (Exception e) {
            return monthYear;
        }
    }

    private int getMonthNumber(String monthName) {
        String lower = monthName.toLowerCase();
        if (lower.startsWith("jan")) return 1;
        if (lower.startsWith("feb")) return 2;
        if (lower.startsWith("mar")) return 3;
        if (lower.startsWith("apr")) return 4;
        if (lower.startsWith("may")) return 5;
        if (lower.startsWith("jun")) return 6;
        if (lower.startsWith("jul")) return 7;
        if (lower.startsWith("aug")) return 8;
        if (lower.startsWith("sep")) return 9;
        if (lower.startsWith("oct")) return 10;
        if (lower.startsWith("nov")) return 11;
        if (lower.startsWith("dec")) return 12;
        return 1;
    }
}
