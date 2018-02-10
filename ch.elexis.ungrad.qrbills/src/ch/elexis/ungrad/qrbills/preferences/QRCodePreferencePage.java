package ch.elexis.ungrad.qrbills.preferences;

import java.util.HashMap;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.Hyperlink;
import org.eclipse.ui.IWorkbench;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.dialogs.KontaktSelektor;
import ch.elexis.core.ui.preferences.SettingsPreferenceStore;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Mandant;
import ch.elexis.data.Query;


public class QRCodePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
	private Combo cbMandanten;
	private Hyperlink hlBank;
	private Text txBank;
	private Label lbIban;
	private Text txIban;
	private Mandant currentMandator;
	private int currentIndex;
	
	public QRCodePreferencePage(){
		setPreferenceStore(new SettingsPreferenceStore(CoreHub.globalCfg));
		setDescription("Bankverbindung für Rechnungen");
	}
	

	public void init(IWorkbench workbench){
	}
	private void setMandant(Mandant m) {
		currentMandator=m;
		String bankID=(String)currentMandator.getExtInfoStoredObjectByKey(PreferenceConstants.QRBANK);
		if(bankID==null) {
			txBank.setText("");
		}else {
			Kontakt bank=Kontakt.load(bankID);
			if(bank.isValid()) {
				txBank.setText(bank.getLabel());
			}else {
				txBank.setText("");
			}
		}
		String iban=(String) currentMandator.getExtInfoStoredObjectByKey(PreferenceConstants.QRIBAN);
		if(iban==null) {
			txIban.setText("");
		}else {
			txIban.setText(iban);
		}
	}	
	
	@Override
	protected Control createContents(Composite parent){
		Color blau = UiDesk.getColor(UiDesk.COL_BLUE);
		Composite ret = new Composite(parent, SWT.NONE);
		ret.setLayout(new GridLayout(2,false));
		cbMandanten=new Combo(ret, SWT.READ_ONLY);
		cbMandanten.setLayoutData(SWTHelper.getFillGridData(2, true, 1, false));
		hlBank=new Hyperlink(ret,SWT.NONE);
		hlBank.setLayoutData(SWTHelper.getFillGridData(1,true,1,false));
		hlBank.setText("Bank");
		hlBank.setForeground(blau);
		txBank=new Text(ret,SWT.READ_ONLY);
		txBank.setLayoutData(SWTHelper.getFillGridData(1,true,1,false));
		lbIban=new Label(ret,SWT.NONE);
		lbIban.setLayoutData(SWTHelper.getFillGridData(1,true,1,false));
		lbIban.setText("IBAN");
		txIban=new Text(ret,SWT.NONE);
		txIban.setLayoutData(SWTHelper.getFillGridData(1,true,1,false));
		HashMap<String,Mandant> hMandanten=new HashMap<>();
		cbMandanten.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e){
				int i = cbMandanten.getSelectionIndex();
				if (i == -1) {
					return;
				}
				setMandant((Mandant) hMandanten.get(cbMandanten.getItem(i)));
				
			}
			
		});
		setMandant(ElexisEventDispatcher.getSelectedMandator());
		Query<Mandant> qbe = new Query<Mandant>(Mandant.class);
		List<Mandant> list = qbe.execute();
		
		for (Mandant m : list) {
			cbMandanten.add(m.getLabel());
			hMandanten.put(m.getLabel(), m);
		}
		cbMandanten.setText(currentMandator.getLabel());
		hlBank.addHyperlinkListener(new HyperlinkAdapter() {

			@Override
			public void linkActivated(HyperlinkEvent e){
				KontaktSelektor ksel=new KontaktSelektor(getShell(), Kontakt.class, "Bank auswählen", 
					"Bitte wählen Sie die Bank aus", new String[] {"Bezeichnung1","Bezeichnung2"});
				if(ksel.open()==Dialog.OK) {
					Kontakt bank=(Kontakt)ksel.getSelection();
					currentMandator.setExtInfoStoredObjectByKey(PreferenceConstants.QRBANK, bank.getId());
					txBank.setText(bank.getLabel());
				}
			}
			
		});
		txIban.addFocusListener(new FocusAdapter() {

			@Override
			public void focusLost(FocusEvent e){
				String iban=txIban.getText().toUpperCase();
				if(iban.length()!=21 || (!iban.startsWith("CH") && !iban.startsWith("LI"))) {
					SWTHelper.showError("Falsche IBAN", "Eine zuässige IBAN muss genau 21 Zeichen lang sein und mit CH oder LI beginnen.");
				}else {
					currentMandator.setExtInfoStoredObjectByKey(PreferenceConstants.QRIBAN, iban);
				}
			}
			
		});
		
		return ret;
	}
	
}