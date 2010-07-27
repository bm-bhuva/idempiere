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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.sax.TransformerHandler;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.pipo2.AbstractElementHandler;
import org.adempiere.pipo2.IPackOutHandler;
import org.adempiere.pipo2.PoExporter;
import org.adempiere.pipo2.Element;
import org.adempiere.pipo2.PackIn;
import org.adempiere.pipo2.PackOut;
import org.adempiere.pipo2.PoFiller;
import org.adempiere.pipo2.exception.POSaveFailedException;
import org.compiere.model.I_AD_Table;
import org.compiere.model.MPackageExp;
import org.compiere.model.MPackageExpDetail;
import org.compiere.model.MTable;
import org.compiere.model.X_AD_Column;
import org.compiere.model.X_AD_Package_Exp_Detail;
import org.compiere.model.X_AD_Package_Imp_Detail;
import org.compiere.model.X_AD_Table;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class TableElementHandler extends AbstractElementHandler implements IPackOutHandler{
	private ColumnElementHandler columnHandler = new ColumnElementHandler();

	private List<Integer>tables = new ArrayList<Integer>();

	public void startElement(Properties ctx, Element element) throws SAXException {
		PackIn packIn = getPackInProcess(ctx);
		List<String> excludes = defaultExcludeList(X_AD_Table.Table_Name);

		String entitytype = getStringValue(element, "EntityType");
		if (isProcessElement(ctx, entitytype)) {

			MTable mTable = findPO(ctx, element);
			if (mTable == null) {
				String tableName = getStringValue(element, "TableName", excludes);

				int id = packIn.getTableId(tableName);
				if (id <= 0) {
					id = findIdByColumn(ctx, "AD_Table", "TableName", tableName);
					if (id > 0)
						packIn.addTable(tableName, id);
				}
				if (id > 0 && isTableProcess(ctx, id)) {
					return;
				}

				mTable = new MTable(ctx, id > 0 ? id : 0, getTrxName(ctx));
				mTable.setTableName(tableName);
			}
						
			if (mTable.getAD_Table_ID() == 0 && isOfficialId(element, "AD_Table_ID"))
			{
				mTable.setAD_Table_ID(getIntValue(element, "AD_Table_ID"));
			}
			
			PoFiller filler = new PoFiller(ctx, mTable, element, this);
			List<String> notfounds = filler.autoFill(excludes);
			if (notfounds.size() > 0) {
				element.defer = true;
				return;
			}
			
			if (mTable.is_new() || mTable.is_Changed()) {
				X_AD_Package_Imp_Detail impDetail = createImportDetail(ctx, element.qName, X_AD_Table.Table_Name,
						X_AD_Table.Table_ID);
				String action = null;
				if (!mTable.is_new()){
					backupRecord(ctx, impDetail.getAD_Package_Imp_Detail_ID(),X_AD_Table.Table_Name,mTable);
					action = "Update";
				}
				else{
					action = "New";				
				}
				if (mTable.save(getTrxName(ctx)) == true){
					logImportDetail (ctx, impDetail, 1, mTable.getName(),mTable.get_ID(),action);
					tables.add(mTable.getAD_Table_ID());
					packIn.addTable(mTable.getTableName(), mTable.getAD_Table_ID());
					element.recordId = mTable.getAD_Table_ID();
				}
				else{
					logImportDetail (ctx, impDetail, 0, mTable.getName(), mTable.get_ID(),action);
					throw new POSaveFailedException("Table");
				}
			}
		} else {
			element.skip = true;
		}
	}

	public void endElement(Properties ctx, Element element) throws SAXException {
	}

	public void create(Properties ctx, TransformerHandler document)
			throws SAXException {

		int AD_Table_ID = Env.getContextAsInt(ctx, X_AD_Package_Exp_Detail.COLUMNNAME_AD_Table_ID);
		PackOut packOut = getPackOutProcess(ctx);
		boolean exported = isTableProcess(ctx, AD_Table_ID);
		AttributesImpl atts = new AttributesImpl();
		//Export table if not already done so
		if (!exported){
			X_AD_Table m_Table = new X_AD_Table (ctx, AD_Table_ID, null);
			addTypeName(atts, "ad.table");
			document.startElement("","",I_AD_Table.Table_Name,atts);
			createTableBinding(ctx,document,m_Table);

			String sql = "SELECT * FROM AD_Column WHERE AD_Table_ID = ? "
				+ " ORDER BY IsKey DESC, AD_Column_ID";  // Export key column as the first one

			PreparedStatement pstmt = null;
			ResultSet rs = null;

			try {
				pstmt = DB.prepareStatement (sql, getTrxName(ctx));
				pstmt.setInt(1, AD_Table_ID);
				rs = pstmt.executeQuery();

				while (rs.next()){
					IPackOutHandler handler = packOut.getHandler("ELE");
					handler.packOut(packOut,null,null,document,null,rs.getInt(X_AD_Column.COLUMNNAME_AD_Element_ID));

					if (rs.getInt(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Reference_ID)>0)
					{
						handler = packOut.getHandler("REF");
						handler.packOut(packOut,null,null,document,null,rs.getInt(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Reference_ID));
					}

					if (rs.getInt("AD_Reference_Value_ID")>0)
					{
						handler = packOut.getHandler("REF");
						handler.packOut(packOut,null,null,document,null,rs.getInt("AD_Reference_Value_ID"));
					}

					if (rs.getInt(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Process_ID)>0)
					{
						handler = packOut.getHandler("P");
						handler.packOut(packOut,null,null,document,null,rs.getInt(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Process_ID));
					}

					if (rs.getInt(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Val_Rule_ID)>0)
					{
						handler = packOut.getHandler("V");
						handler.packOut(packOut,null,null,document,null,rs.getInt(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Val_Rule_ID));
					}

					createColumn(ctx, document, rs.getInt("AD_Column_ID"));
				}
			} catch (Exception e)	{
				throw new AdempiereException(e);
			} finally	{
				DB.close(rs, pstmt);
			}
			document.endElement("","",I_AD_Table.Table_Name);
		}

	}

	private void createColumn(Properties ctx, TransformerHandler document, int AD_Column_ID) throws SAXException {
		Env.setContext(ctx, X_AD_Column.COLUMNNAME_AD_Column_ID, AD_Column_ID);
		columnHandler.create(ctx, document);
		ctx.remove(X_AD_Column.COLUMNNAME_AD_Column_ID);
	}

	private boolean isTableProcess(Properties ctx, int AD_Table_ID) {
		if (tables.contains(AD_Table_ID))
			return true;
		else {
			tables.add(AD_Table_ID);
			return false;
		}
	}

	private void createTableBinding(Properties ctx, TransformerHandler document, X_AD_Table m_Table)
	{
		PoExporter filler = new PoExporter(ctx, document, m_Table);
		if (m_Table.getAD_Table_ID() <= PackOut.MAX_OFFICIAL_ID)
		{
			filler.add("AD_Table_ID", new AttributesImpl());
		}

		List<String> excludes = defaultExcludeList(X_AD_Table.Table_Name);		
		filler.export(excludes);
	}

	public void packOut(PackOut packout, MPackageExp header, MPackageExpDetail detail,TransformerHandler packOutDocument,TransformerHandler packageDocument,int recordId) throws Exception
	{
		if(recordId <= 0)
			recordId = detail.getAD_Table_ID();

		Env.setContext(packout.getCtx(), X_AD_Package_Exp_Detail.COLUMNNAME_AD_Table_ID, recordId);

		this.create(packout.getCtx(), packOutDocument);
		packout.getCtx().remove(X_AD_Package_Exp_Detail.COLUMNNAME_AD_Table_ID);
	}
}