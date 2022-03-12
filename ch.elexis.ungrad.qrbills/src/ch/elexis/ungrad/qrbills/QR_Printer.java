package ch.elexis.ungrad.qrbills;

import java.io.File;
import java.io.IOException;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;

import ch.rgw.tools.StringTool;

import java.awt.print.*;

public class QR_Printer {

	/**
	 * Print a pdf from a File
	 * 
	 * @param pdfFile The File. Must be PDF
	 * @param printer Name or part of the name of the printer to chose. Can be null,
	 *                then a print dialog is displayed
	 * @return true if the file was printed
	 * @throws IOException
	 * @throws PrinterException
	 */
	public boolean print(File pdfFile, String printer) throws IOException, PrinterException {
		PDDocument pdoc = PDDocument.load(pdfFile);
		PDFPrintable printable = new PDFPrintable(pdoc);
		PrinterJob job = PrinterJob.getPrinterJob();
		job.setPrintable(printable);
		if (!StringTool.isNothing(printer)) {
			PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
			int selectedService = 0;
			/* Scan found services to see if anyone suits our needs */
			for (int i = 0; i < services.length; i++) {
				if (services[i].getName().toUpperCase().contains(printer)) {
					selectedService = i;
					break;
				}
			}
			job.setPrintService(services[selectedService]);
			job.print();
			return true;

		} else {
			if (job.printDialog()) {
				job.print();
				return true;
			}
			return false;
		}
	}
}
