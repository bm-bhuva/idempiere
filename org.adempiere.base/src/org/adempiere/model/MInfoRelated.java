/******************************************************************************
 * Product: iDempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/

package org.adempiere.model;

import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.I_AD_InfoRelated;
import org.compiere.model.MInfoColumn;
import org.compiere.model.Query;
import org.compiere.model.X_AD_InfoRelated;

public class MInfoRelated extends X_AD_InfoRelated implements I_AD_InfoRelated {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7899684001732574833L;

	public MInfoRelated(Properties ctx, int AD_InfoRelated_ID, String trxName) {
		super(ctx, AD_InfoRelated_ID, trxName);
	}

	public MInfoRelated(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	public MInfoColumn getLinkInfoColumn() {
		if (log.isLoggable(Level.INFO)) log.info("Link Column ID: -----  : " + getRelatedColumn_ID());

		MInfoColumn infoCol = (MInfoColumn) new Query(getCtx(), "AD_InfoColumn", "AD_InfoColumn_ID=?", get_TrxName())
			.setParameters(getRelatedColumn_ID())
			.first();

		return infoCol;
	}

	public String getLinkColumnName() {
		MInfoColumn infoCol = getLinkInfoColumn();
		if (infoCol != null)
			return infoCol.getSelectClause();
		else
			return "";
	}

}
