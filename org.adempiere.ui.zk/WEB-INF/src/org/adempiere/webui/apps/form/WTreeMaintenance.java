/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.adempiere.webui.apps.form;

import java.util.ArrayList;
import java.util.logging.Level;

import org.adempiere.util.Callback;
import org.adempiere.webui.LayoutUtils;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.Checkbox;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.SimpleListModel;
import org.adempiere.webui.component.SimpleTreeModel;
import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.CustomForm;
import org.adempiere.webui.panel.IFormController;
import org.adempiere.webui.session.SessionManager;
import org.adempiere.webui.theme.ThemeManager;
import org.adempiere.webui.window.FDialog;
import org.compiere.apps.form.TreeMaintenance;
import org.compiere.model.MTree;
import org.compiere.model.MTreeNode;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Borderlayout;
import org.zkoss.zul.Cell;
import org.zkoss.zul.Center;
import org.zkoss.zul.DefaultTreeNode;
import org.zkoss.zul.East;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.North;
import org.zkoss.zul.Space;
import org.zkoss.zul.Splitter;
import org.zkoss.zul.Tree;
import org.zkoss.zul.TreeModel;
import org.zkoss.zul.TreeNode;
import org.zkoss.zul.Treeitem;

/**
 *	Tree Maintenance
 *	
 *  @author Jorg Janke
 *  @version $Id: VTreeMaintenance.java,v 1.3 2006/07/30 00:51:28 jjanke Exp $
 */
public class WTreeMaintenance extends TreeMaintenance implements IFormController, EventListener<Event>
{
	/**
	 * 
	 */
	@SuppressWarnings("unused")
	private static final long serialVersionUID = 3630156132596215136L;
	
	private CustomForm form = new CustomForm();	
	
	private Borderlayout	mainLayout	= new Borderlayout ();
	private Panel 			northPanel	= new Panel ();
	private Label			treeLabel	= new Label ();
	private Listbox			treeField;
	private Button			bAddAll		= new Button ();
	private Button			bAdd		= new Button ();
	private Button			bDelete		= new Button ();
	private Button			bDeleteAll	= new Button ();
	private Checkbox		cbAllNodes	= new Checkbox ();
	private Label			treeInfo	= new Label ();
	//
	@SuppressWarnings("unused")
	private Splitter		splitPane	= new Splitter();
	private Tree			centerTree;
	private Listbox			centerList	= new Listbox();

	
	public WTreeMaintenance()
	{
		try
		{
			m_WindowNo = form.getWindowNo();
			preInit();
			jbInit ();
			action_loadTree();
			LayoutUtils.sendDeferLayoutEvent(mainLayout, 100);
		}
		catch (Exception ex)
		{
			log.log(Level.SEVERE, "VTreeMaintenance.init", ex);
		}
	}	//	init
	
	/**
	 * 	Fill Tree Combo
	 */
	private void preInit()
	{
		treeField = new Listbox(getTreeData());
		treeField.setMold("select");
		treeField.addActionListener(this);
		treeField.setSelectedIndex(0);
		//
		centerTree = new Tree();
		centerTree.addEventListener(Events.ON_SELECT, this);
	}	//	preInit
	
	/**
	 * 	Static init
	 *	@throws Exception
	 */
	private void jbInit () throws Exception
	{
		bAddAll.setImage(ThemeManager.getThemeResource("images/FastBack24.png"));
		bAdd.setImage(ThemeManager.getThemeResource("images/StepBack24.png"));
		bDelete.setImage(ThemeManager.getThemeResource("images/StepForward24.png"));
		bDeleteAll.setImage(ThemeManager.getThemeResource("images/FastForward24.png"));
		
		form.setWidth("99%");
		form.setHeight("100%");
		form.setStyle("position: absolute; padding: 0; margin: 0");
		form.appendChild (mainLayout);
		mainLayout.setWidth("100%");
		mainLayout.setHeight("100%");
		mainLayout.setStyle("position: absolute");
		
		treeLabel.setText (Msg.translate(Env.getCtx(), "AD_Tree_ID"));
		cbAllNodes.setEnabled (false);
		cbAllNodes.setText (Msg.translate(Env.getCtx(), "IsAllNodes"));
		treeInfo.setText (" ");
		bAdd.setTooltiptext("Add to Tree");
		bAddAll.setTooltiptext("Add ALL to Tree");
		bDelete.setTooltiptext("Delete from Tree");
		bDeleteAll.setTooltiptext("Delete ALL from Tree");
		bAdd.addActionListener(this);
		bAddAll.addActionListener(this);
		bDelete.addActionListener(this);
		bDeleteAll.addActionListener(this);
		
		North north = new North();
		mainLayout.appendChild(north);
		north.appendChild(northPanel);
		northPanel.setWidth("100%");
		//
		Hbox hbox = new Hbox();
		hbox.setStyle("padding: 3px;");
		hbox.setAlign("center");
		hbox.setHflex("1");
		northPanel.appendChild(hbox);
		
		hbox.appendChild (new Space());
		hbox.appendChild (treeLabel);
		hbox.appendChild (treeField);
		hbox.appendChild (new Space());
		hbox.appendChild (cbAllNodes);
		hbox.appendChild (new Space());
		Cell cell = new Cell();
		cell.setColspan(1);
		cell.setRowspan(1);
		cell.setHflex("1");
		cell.appendChild(treeInfo);
		hbox.appendChild (cell);
		hbox.appendChild (new Space());
		hbox.appendChild (bAddAll);
		hbox.appendChild (bAdd);
		hbox.appendChild (bDelete);
		hbox.appendChild (bDeleteAll);
		//
		Center center = new Center();
		mainLayout.appendChild(center);	
		center.appendChild(centerTree);
		centerTree.setVflex("1");
		centerTree.setHflex("1");
		center.setAutoscroll(true);
		
		East east = new East();
		mainLayout.appendChild(east);
		east.appendChild(centerList);
		east.setCollapsible(false);
		east.setSplittable(true);
		east.setWidth("45%");
		centerList.setVflex(true);
		centerList.setSizedByContent(false);
		centerList.addEventListener(Events.ON_SELECT, this);
	}	//	jbInit

	/**
	 * 	Dispose
	 */
	public void dispose()
	{
		SessionManager.getAppDesktop().closeActiveWindow();
	}	//	dispose

	/**
	 * 	Action Listener
	 *	@param e event
	 */
	public void onEvent (Event e)
	{
		if (e.getTarget() == treeField)
		{
			action_loadTree();
			LayoutUtils.sendDeferLayoutEvent(mainLayout, 100);
		}
		else if (e.getTarget() == bAddAll)
			action_treeAddAll();
		else if (e.getTarget() == bAdd)
		{
			SimpleListModel model = (SimpleListModel) centerList.getModel();
			int i = centerList.getSelectedIndex();
			if (i >= 0) {
				action_treeAdd((ListItem)model.getElementAt(i));
			}
		}
			
		else if (e.getTarget() == bDelete)
		{
			SimpleListModel model = (SimpleListModel) centerList.getModel();
			int i = centerList.getSelectedIndex();
			if (i >= 0) {
				action_treeDelete((ListItem)model.getElementAt(i));
			}
		}			
		else if (e.getTarget() == bDeleteAll)
			action_treeDeleteAll();
		else if (e.getTarget() == centerList)
			onListSelection(e);
		else if (e.getTarget() == centerTree)
			onTreeSelection(e);
	}	//	actionPerformed

	
	/**
	 * 	Action: Fill Tree with all nodes
	 */
	private void action_loadTree()
	{
		KeyNamePair tree = treeField.getSelectedItem().toKeyNamePair();
		log.info("Tree=" + tree);
		if (tree.getKey() <= 0)
		{
			SimpleListModel tmp = new SimpleListModel();
			centerList.setItemRenderer(tmp);
			centerList.setModel(tmp);
			return;
		}
		//	Tree
		m_tree = new MTree (Env.getCtx(), tree.getKey(), null);
		cbAllNodes.setSelected(m_tree.isAllNodes());
		bAddAll.setEnabled(!m_tree.isAllNodes());
		bAdd.setEnabled(!m_tree.isAllNodes());
		bDelete.setEnabled(!m_tree.isAllNodes());
		bDeleteAll.setEnabled(!m_tree.isAllNodes());
		//
		/*String fromClause = m_tree.getSourceTableName(false);	//	fully qualified
		String columnNameX = m_tree.getSourceTableName(true);
		String actionColor = m_tree.getActionColorName();*/
		
		//	List
		SimpleListModel model = new SimpleListModel();
		ArrayList<ListItem> items = getTreeItemData();
		for(ListItem item : items)
			model.addElement(item);
		
		if (log.isLoggable(Level.CONFIG)) log.config("#" + model.getSize());
		centerList.setItemRenderer(model);
		centerList.setModel(model);
		
		//	Tree
		try {
			centerTree.setModel(null);
		} catch (Exception e) {
		}
		if (centerTree.getTreecols() != null)
			centerTree.getTreecols().detach();
		if (centerTree.getTreefoot() != null)
			centerTree.getTreefoot().detach();
		if (centerTree.getTreechildren() != null)
			centerTree.getTreechildren().detach();
		
		SimpleTreeModel.initADTree(centerTree, m_tree.getAD_Tree_ID(), m_WindowNo);
	}	//	action_fillTree
	
	/**
	 * 	List Selection Listener
	 *	@param e event
	 */
	private void onListSelection(Event e)
	{
		ListItem selected = null;
		try		
		{	
			SimpleListModel model = (SimpleListModel) centerList.getModel();
			int i = centerList.getSelectedIndex();
			selected = (ListItem)model.getElementAt(i);
		}
		catch (Exception ex)
		{
		}
		log.info("Selected=" + selected);
		if (selected != null)	//	allow add if not in tree
		{
			SimpleTreeModel tm = (SimpleTreeModel)(TreeModel<?>) centerTree.getModel();
			DefaultTreeNode<Object> stn = tm.find(tm.getRoot(), selected.id);
			if (stn != null) {
				int[] path = tm.getPath(stn);
				Treeitem ti = centerTree.renderItemByPath(path);
				ti.setSelected(true);
			}
			bAdd.setEnabled(stn == null);
		}
	}	//	valueChanged
	
	/**
	 * 	Tree selection
	 *	@param e event
	 */
	private void onTreeSelection (Event e)
	{
		Treeitem ti = centerTree.getSelectedItem();
		DefaultTreeNode<?> stn = (DefaultTreeNode<?>) ti.getValue();
		MTreeNode tn = (MTreeNode)stn.getData();
		if (tn == null)
			return;
		log.info(tn.toString());
		ListModel<Object> model = centerList.getModel();
		int size = model.getSize();
		int index = -1;
		for (index = 0; index < size; index++)
		{
			ListItem item = (ListItem)model.getElementAt(index);
			if (item.id == tn.getNode_ID())
				break;
		}
		centerList.setSelectedIndex(index);
	}	//	propertyChange

	/**
	 * 	Action: Add Node to Tree
	 * 	@param item item
	 */
	private void action_treeAdd(ListItem item)
	{
		log.info("Item=" + item);
		if (item != null)
		{
			SimpleTreeModel model = (SimpleTreeModel)(TreeModel<?>) centerTree.getModel();
			DefaultTreeNode<Object> stn = model.find(model.getRoot(), item.id);
			if (stn != null) {
				MTreeNode tNode = (MTreeNode) stn.getData();
				tNode.setName(item.name);
				tNode.setAllowsChildren(item.isSummary);
				tNode.setImageIndicator(item.imageIndicator);
				model.nodeUpdated(stn);
				Treeitem ti = centerTree.renderItemByPath(model.getPath(stn));
				ti.setTooltiptext(item.description);
			} else {
				stn = new DefaultTreeNode<Object>(new MTreeNode(item.id, 0, item.name, item.description, 0, item.isSummary,
						item.imageIndicator, false, null), new ArrayList<TreeNode<Object>>());
				model.addNode(stn);
			}
			//	May cause Error if in tree
			addNode(item);
		}
	}	//	action_treeAdd
	
	/**
	 * 	Action: Delete Node from Tree
	 * 	@param item item
	 */
	private void action_treeDelete(ListItem item)
	{
		log.info("Item=" + item);
		if (item != null)
		{
			SimpleTreeModel model = (SimpleTreeModel)(TreeModel<?>) centerTree.getModel();
			DefaultTreeNode<Object> stn = model.find(model.getRoot(), item.id);
			if (stn != null)
				model.removeNode(stn);
			
			//
			deleteNode(item);
		}
	}	//	action_treeDelete

	
	/**
	 * 	Action: Add All Nodes to Tree
	 */
	private void action_treeAddAll()
	{
		// idempiere-85
		FDialog.ask(m_WindowNo, null, "TreeAddAllItems", new Callback<Boolean>() {
			
			@Override
			public void onCallback(Boolean result) 
			{
				if (result)
				{
					log.info("");
					ListModel<Object> model = centerList.getModel();
					int size = model.getSize();
					int index = -1;
					for (index = 0; index < size; index++)
					{
						ListItem item = (ListItem)model.getElementAt(index);
						action_treeAdd(item);
					}
				}
			}
		});
	}	//	action_treeAddAll
	
	/**
	 * 	Action: Delete All Nodes from Tree
	 */
	private void action_treeDeleteAll()
	{
		log.info("");
		// idempiere-85
		FDialog.ask(m_WindowNo, null, "TreeRemoveAllItems", new Callback<Boolean>() {

			@Override
			public void onCallback(Boolean result) 
			{
				if (result)
				{
					ListModel<Object> model = centerList.getModel();
					int size = model.getSize();
					int index = -1;
					for (index = 0; index < size; index++)
					{
						ListItem item = (ListItem)model.getElementAt(index);
						action_treeDelete(item);
					}
				}
				
			}
		});
	}	//	action_treeDeleteAll
	
	public ADForm getForm() 
	{
		return form;
	}

}	//	VTreeMaintenance
