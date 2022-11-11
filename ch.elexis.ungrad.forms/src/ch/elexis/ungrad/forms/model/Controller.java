/*******************************************************************************
 * Copyright (c) 2022, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 *******************************************************************************/

package ch.elexis.ungrad.forms.model;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jface.viewers.IStructuredContentProvider;

import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.text.XRefExtensionConstants;
import ch.elexis.core.ui.util.viewers.TableLabelProvider;
import ch.elexis.data.Brief;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Patient;
import ch.elexis.ungrad.forms.Activator;
import ch.elexis.ungrad.pdf.Manager;
import ch.rgw.io.FileTool;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

/**
 * Handle forms
 * 
 * @author gerry
 *
 */
public class Controller extends TableLabelProvider implements IStructuredContentProvider {
	private Patient currentPatient;

	void changePatient(Patient pat) {
		currentPatient = pat;
	}

	/**
	 * Find the configured output dir for a patient (highly opinionated filepath
	 * resolution)
	 * 
	 * @param p Patient whos output dir should be retrieved
	 * @return The directory to store documents for that patient.
	 */
	public File getOutputDirFor(Patient p) {
		if (p == null) {
			p = ElexisEventDispatcher.getSelectedPatient();
		}
		String name = p.getName();
		String fname = p.getVorname();
		String birthdate = p.getGeburtsdatum();
		File superdir = new File(CoreHub.localCfg.get(PreferenceConstants.OUTPUT, ""),
				name.substring(0, 1).toLowerCase());
		File dir = new File(superdir, name + "_" + fname + "_" + birthdate);
		return dir;
	}

	/* CoontentProvider */
	@Override
	public Object[] getElements(Object inputElement) {
		Patient pat = (Patient) inputElement;
		File dir = getOutputDirFor(pat);
		String[] files = dir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				if (name.startsWith("A_")) {
					String ext = FileTool.getExtension(name);
					return ext.equalsIgnoreCase("pdf") || ext.equalsIgnoreCase("html");
				} else {
					return false;
				}
			}
		});
		if (files == null) {
			return new String[0];
		} else {
			Set<String> deduplicated = new LinkedHashSet<String>();
			for (String file : files) {
				deduplicated.add(FileTool.getNakedFilename(file));
			}
			String[] ret = deduplicated.toArray(new String[0]);
			return ret;
		}
	}

	/* LabelProvider */
	@Override
	public String getColumnText(Object element, int columnIndex) {
		return (String) element;
	}

	/**
	 * Return the output dir for a given patient.
	 * 
	 * @param pat The patient whose output dir should be returned. Will be created
	 *            if doesn't exist
	 * 
	 * @return
	 */
	public File getOutputDir(Patient pat) throws Exception {
		String dirname = pat.getName() + "_" + pat.getVorname() + "_" + pat.getGeburtsdatum();

		StringBuilder sb = new StringBuilder();
		sb.append(CoreHub.localCfg.get(PreferenceConstants.OUTPUT, "")).append(File.separator)
				.append(dirname.substring(0, 1).toLowerCase()).append(File.separator).append(dirname);
		File dir = new File(sb.toString());
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new Exception("Can't create output dir");
			}
		}
		return dir;
	}

	/**
	 * Write an HTML file from the current state of the Template. This is called
	 * with every deactivation of the Forms View to save current work if
	 * interrupted. And it's called befor pdf outputting.
	 * 
	 * @param tmpl The Template to save
	 * @return The HTML file just written
	 * @throws Exception
	 */
	public File writeHTML(Template tmpl) throws Exception {
		String filename = tmpl.getFilename();
		String prefix = "";
		File htmlFile;
		if (StringTool.isNothing(filename)) {
			String doctype = tmpl.getDoctype();
			if (!StringTool.isNothing(doctype)) {
				tmpl.replace("Doctype", doctype);
			}
			String doctitle = tmpl.getTitle();
			if (!StringTool.isNothing(doctitle)) {
				tmpl.replace("Doctitle", doctitle);
			}

			filename = new TimeTool().toString(TimeTool.FULL_ISO);
			prefix = "A_" + new TimeTool().toString(TimeTool.DATE_ISO) + "_";
			if (!StringTool.isNothing(tmpl.getTitle())) {
				prefix += tmpl.getTitle() + "_";
			}
			if (tmpl.adressat != null) {
				filename = prefix + tmpl.adressat.get(Kontakt.FLD_NAME1) + "_" + tmpl.adressat.get(Kontakt.FLD_NAME2);
			} else {
				String name = "Ausgang";
				filename = prefix + name;
			}
			Patient pat = ElexisEventDispatcher.getSelectedPatient();

			File dir = getOutputDir(pat);
			if (!dir.exists()) {
				if (!dir.mkdirs()) {
					throw new Exception("Could not create directory " + dir.getAbsolutePath());
				}
			}
			htmlFile = new File(dir, filename + ".html");
		} else {
			htmlFile = new File(filename);
		}
		String content = tmpl.getXml();
		FileTool.writeTextFile(htmlFile, content);
		return htmlFile;
	}

	/**
	 * Create a PDF file from a template. Connect an Elexis "Brief" to the resulting
	 * file, If there is already a Brief connected, don't create a new one but
	 * update the existing (i.e. if the user just modified and re-printed an
	 * existing document.
	 * 
	 * @param htmlFile The HTML file
	 * @param tmpl     The Template to use
	 * @return the full path of the pdf written
	 * @throws Exception
	 */
	public String createPDF(File htmlFile, Template template) throws Exception {

		Manager pdf = new Manager();
		File pdfFile = new File(htmlFile.getParent(), FileTool.getNakedFilename(htmlFile.getName()) + ".pdf");
		pdf.createPDF(htmlFile, pdfFile);
		String outputFile = pdfFile.getAbsolutePath();
		String brief = template.getBrief();
		if (brief == null) {
			Brief meta = createLinksWithElexis(outputFile, template.adressat);
			template.setBrief(meta);
		} else {
			Brief meta = Brief.load(brief);
			if (meta.isValid()) {
				meta.save(FileTool.readFile(pdfFile), "pdf");
			}

		}
		return outputFile;
	}

	public Brief createLinksWithElexis(String filepath, Kontakt adressat) throws Exception {
		String briefTitle = FileTool.getNakedFilename(filepath);
		if (briefTitle.matches("A_[0-9]{4,4}-[0-1][0-9]-[0-3][0-9]_.+")) {
			briefTitle = briefTitle.substring(13);
		}
		Konsultation current = (Konsultation) ElexisEventDispatcher.getInstance().getSelected(Konsultation.class);
		Brief metadata = new Brief(briefTitle, new TimeTool(), CoreHub.actUser, adressat, current, "Formular");
		metadata.save(FileTool.readFile(new File(filepath)), "pdf");
		addFormToKons(metadata, current);
		return metadata;
	}

	private void addFormToKons(final Brief brief, final Konsultation kons) {
		if (kons != null) {
			if (CoreHub.getLocalLockService().acquireLock(kons).isOk()) {
				String label = "[ " + brief.getLabel().replace("_", " ") + " ]"; //$NON-NLS-1$ //$NON-NLS-2$
				// kons.addXRef(XRefExtensionConstants.providerID, brief.getId(), -1, label);
				kons.addXRef(Activator.KonsXRef, brief.getId(), -1, label);
				CoreHub.getLocalLockService().releaseLock(kons);
			}
		}
	}

	public String convertPug(String pug, String dir) throws Exception {
		dir += File.separator + "x";
		String pugbin = CoreHub.localCfg.get(PreferenceConstants.PUG, "pug");

		Process process = new ProcessBuilder(pugbin, "-p", dir).start();
		InputStreamReader err = new InputStreamReader(process.getErrorStream());
		BufferedReader burr = new BufferedReader(err);
		InputStreamReader ir = new InputStreamReader(process.getInputStream());
		BufferedReader br = new BufferedReader(ir);
		OutputStreamWriter ow = new OutputStreamWriter(process.getOutputStream());
		ow.write(pug);
		ow.flush();
		ow.close();
		String line;
		StringBuilder sb = new StringBuilder();
		StringBuilder serr = new StringBuilder();
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}
		while ((line = burr.readLine()) != null) {
			serr.append(line);
		}
		String errmsg = serr.toString();
		if (StringTool.isNothing(errmsg)) {
			return sb.toString();
		} else {
			throw new Error(errmsg);
		}
	}

	/**
	 * Revove documents from output dir (not from database)
	 * @param item 
	 * @param pat
	 * @throws Exception
	 */
	public void delete(String item, Patient pat) throws Exception {
		File dir = getOutputDir(pat);
		File htmlFile = new File(dir, item + ".html");
		File pdfFile = new File(dir, item + ".pdf");
		if (pdfFile.exists()) {
			pdfFile.delete();
		}
		if (htmlFile.exists()) {
			htmlFile.delete();
		}
	}
}
