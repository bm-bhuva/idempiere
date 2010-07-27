/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 Adempiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *
 * Copyright (C) 2005 Robert Klein. robeklein@hotmail.com
 * Contributor(s): Low Heng Sin hengsin@avantz.com
 *****************************************************************************/
package org.adempiere.pipo2.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.sax.TransformerHandler;

import org.adempiere.pipo2.AbstractElementHandler;
import org.adempiere.pipo2.IPackOutHandler;
import org.adempiere.pipo2.PoExporter;
import org.adempiere.pipo2.Element;
import org.adempiere.pipo2.PackOut;
import org.adempiere.pipo2.PoFiller;
import org.adempiere.pipo2.exception.POSaveFailedException;
import org.compiere.model.I_AD_Val_Rule;
import org.compiere.model.MPackageExp;
import org.compiere.model.MPackageExpDetail;
import org.compiere.model.X_AD_Package_Exp_Detail;
import org.compiere.model.X_AD_Package_Imp_Detail;
import org.compiere.model.X_AD_Val_Rule;
import org.compiere.util.Env;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class DynValRuleElementHandler extends AbstractElementHandler implements IPackOutHandler {

	private List<Integer> rules = new ArrayList<Integer>();

	public void startElement(Properties ctx, Element element) throws SAXException {
		String entitytype = getStringValue(element, "EntityType");
		if (isProcessElement(ctx, entitytype)) {
			String name = getStringValue(element, "Name");
			
			X_AD_Val_Rule mValRule = findPO(ctx, element);
			if (mValRule == null)
			{
				int id = findIdByColumn(ctx, "AD_Val_Rule", "Name", name);
				mValRule = new X_AD_Val_Rule(ctx, id > 0 ? id : 0, getTrxName(ctx));
			}
			if (mValRule.getAD_Val_Rule_ID() == 0 && isOfficialId(element, "AD_Val_Rule_ID"))
				mValRule.setAD_Val_Rule_ID(getIntValue(element, "AD_Val_Rule_ID"));

			List<String> excludes = defaultExcludeList(X_AD_Val_Rule.Table_Name);

			PoFiller filler = new PoFiller(ctx, mValRule, element, this);
			List<String> notfounds = filler.autoFill(excludes);
			if (notfounds.size() > 0) {
				element.defer = true;
				return;
			}
			
			if (mValRule.is_new() || mValRule.is_Changed()) {
				X_AD_Package_Imp_Detail impDetail = createImportDetail(ctx, element.qName, X_AD_Val_Rule.Table_Name,
						X_AD_Val_Rule.Table_ID);
				String action = null;
				if (!mValRule.is_new()){
					backupRecord(ctx, impDetail.getAD_Package_Imp_Detail_ID(), X_AD_Val_Rule.Table_Name, mValRule);
					action = "Update";
				}
				else{
					action = "New";
				}
	
				if (mValRule.save(getTrxName(ctx)) == true){
					logImportDetail (ctx, impDetail, 1, mValRule.getName(), mValRule.get_ID(),action);
				}
				else{
					logImportDetail (ctx, impDetail, 0, mValRule.getName(), mValRule.get_ID(),action);
					throw new POSaveFailedException("Failed to save dynamic validation rule.");
				}
			}
		} else {
			element.skip = true;
		}

	}

	public void endElement(Properties ctx, Element element) throws SAXException {
	}

	protected void create(Properties ctx, TransformerHandler document)
			throws SAXException {
		int AD_Val_Rule_ID = Env.getContextAsInt(ctx, X_AD_Package_Exp_Detail.COLUMNNAME_AD_Val_Rule_ID);
		if (rules.contains(AD_Val_Rule_ID))
			return;
		rules.add(AD_Val_Rule_ID);
		X_AD_Val_Rule m_ValRule = new X_AD_Val_Rule (ctx, AD_Val_Rule_ID, null);
		AttributesImpl atts = new AttributesImpl();
		addTypeName(atts, "ad.dynamic-validation");
		document.startElement("","",I_AD_Val_Rule.Table_Name, atts);
		createDynamicValidationRuleBinding(ctx,document,m_ValRule);
		document.endElement("","",I_AD_Val_Rule.Table_Name);

	}

	private void createDynamicValidationRuleBinding(Properties ctx, TransformerHandler document, X_AD_Val_Rule m_ValRule)
	{
		PoExporter filler = new PoExporter(ctx, document, m_ValRule);
		List<String>excludes = defaultExcludeList(X_AD_Val_Rule.Table_Name);

		if (m_ValRule.getAD_Val_Rule_ID() <= PackOut.MAX_OFFICIAL_ID)
			filler.add("AD_Val_Rule_ID", new AttributesImpl());

		filler.export(excludes);
	}


	public void packOut(PackOut packout, MPackageExp header, MPackageExpDetail detail,TransformerHandler packOutDocument,TransformerHandler packageDocument,int recordId) throws Exception
	{

		if(recordId <= 0 )
			recordId = detail.getAD_Val_Rule_ID();

		Env.setContext(packout.getCtx(), X_AD_Package_Exp_Detail.COLUMNNAME_AD_Val_Rule_ID, recordId);

		this.create(packout.getCtx(), packOutDocument);
		packout.getCtx().remove(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Val_Rule_ID);
	}
}