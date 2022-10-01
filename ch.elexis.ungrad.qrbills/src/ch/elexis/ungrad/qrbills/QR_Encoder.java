package ch.elexis.ungrad.qrbills;

import ch.codeblock.qrinvoice.model.QrInvoice;
import ch.codeblock.qrinvoice.model.builder.QrInvoiceBuilder;

import java.io.UnsupportedEncodingException;

import ch.codeblock.qrinvoice.OutputFormat;
import ch.codeblock.qrinvoice.QrInvoiceCodeCreator;
import ch.codeblock.qrinvoice.output.QrCode;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Rechnung;
import ch.rgw.crypt.BadParameterException;

/**
 * Create the QR-Code as image
 * uses the qr-invoice library (https://docs.qr-invoice.ch/latest/welcome/index.html)
 * @author gerry
 *
 */
public class QR_Encoder {
	public byte[] generate(BillDetails bill)
			throws BadParameterException, UnsupportedEncodingException {
		final QrInvoice qr = QrInvoiceBuilder.create().creditorIBAN(bill.qrIBAN)
				.paymentAmountInformation(p -> p.chf(bill.amountDue.getAmount()))
				.creditor(c -> c.combinedAddress()
						.name(bill.biller.get(Kontakt.FLD_NAME1) + " " + bill.biller.get(Kontakt.FLD_NAME2))
						.addressLine1(bill.biller.get(Kontakt.FLD_STREET))
						.addressLine2(bill.biller.get(Kontakt.FLD_ZIP) + " " + bill.biller.get(Kontakt.FLD_PLACE))
						.country("CH"))
				.ultimateDebtor(d -> d.combinedAddress()
						.name(bill.adressat.get(Kontakt.FLD_NAME1) + " " + bill.adressat.get(Kontakt.FLD_NAME2))
						.addressLine1(bill.adressat.get(Kontakt.FLD_STREET))
						.addressLine2(bill.adressat.get(Kontakt.FLD_ZIP) + " " + bill.adressat.get(Kontakt.FLD_PLACE))
						.country("CH"))
				.paymentReference(r -> r.qrReference(bill.qrReference)).build();

		final QrCode qrCode = QrInvoiceCodeCreator.create().qrInvoice(qr).outputFormat(OutputFormat.PNG)
				.desiredQrCodeSize(500).createQrCode();
		final byte[] image = qrCode.getData();
		return image;
	}
}
