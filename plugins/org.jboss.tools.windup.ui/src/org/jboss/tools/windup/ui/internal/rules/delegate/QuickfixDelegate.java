/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package org.jboss.tools.windup.ui.internal.rules.delegate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.wst.xml.core.internal.contentmodel.CMAttributeDeclaration;
import org.eclipse.wst.xml.core.internal.contentmodel.CMElementDeclaration;
import org.eclipse.wst.xml.core.internal.contentmodel.CMNode;
import org.eclipse.wst.xml.core.internal.contentmodel.modelquery.ModelQuery;
import org.eclipse.wst.xml.core.internal.contentmodel.modelquery.ModelQueryAction;
import org.jboss.tools.windup.ui.internal.RuleMessages;
import org.jboss.tools.windup.ui.internal.editor.ElementAttributesContainer;
import org.jboss.tools.windup.ui.internal.editor.RulesetElementUiDelegateFactory.ChoiceAttributeRow;
import org.jboss.tools.windup.ui.internal.editor.RulesetElementUiDelegateFactory.NodeRow;
import org.jboss.tools.windup.ui.internal.editor.RulesetElementUiDelegateFactory.RulesetConstants;
import org.jboss.windup.tooling.data.QuickfixType;
import org.w3c.dom.Element;

import com.google.common.base.Objects;

@SuppressWarnings("restriction")
public class QuickfixDelegate extends ElementUiDelegate {
	
	@Override
	protected boolean shouldFilterElementInsertAction(ModelQueryAction action) {
		return true;
	}
	
	@Override
	protected void createTabs() {
		addTab(DetailsTab.class);
	}
	
	private ChoiceAttributeRow createTypeRow(CMNode cmNode) {
		return new ChoiceAttributeRow(element, cmNode, true) {
			@Override
			protected List<String> getOptions() {
				return Arrays.stream(QuickfixType.values()).map(e -> computeUiValue(e)).
						collect(Collectors.toList());
			}
			@Override
			protected String modelToDisplayValue(String modelValue) {
				if (modelValue == null || modelValue.isEmpty()) {
					return "";
				}
				
				Optional<QuickfixType> type = Arrays.stream(QuickfixType.values()).filter(e -> {
					return Objects.equal(e.name(), modelValue);
				}).findFirst();
				
				if(type.isPresent()) {
					return computeUiValue(type.get());
				}

				return "";
			}
			
			@Override
			protected String displayToModelValue(String uiValue) {
				if (uiValue.isEmpty()) {
					return "";
				}
				
				Optional<QuickfixType> type = Arrays.stream(QuickfixType.values()).filter(e -> {
					return Objects.equal(uiValue, computeUiValue(e));
				}).findFirst(); 
				
				if (type.isPresent()) {
					return String.valueOf(type.get().name());
				}
				return "";
			}
			
			private String computeUiValue(QuickfixType type) {
				return type.name();
			}
		};
	}
	
	@SuppressWarnings("unchecked")
	public void createControls(Composite parent, Element element, CMElementDeclaration ed, List<NodeRow> rows) {
		List<CMAttributeDeclaration> availableAttributeList = modelQuery.getAvailableContent(element, ed, ModelQuery.INCLUDE_ATTRIBUTES);
		for (CMAttributeDeclaration declaration : availableAttributeList) {
		    	if (Objects.equal(declaration.getAttrName(), RulesetConstants.QUICKFIX_TYPE)) {
		    		ChoiceAttributeRow row = createTypeRow(declaration);
		    		rows.add(row);
		    		row.createContents(parent, toolkit, 2);
		    	}
		    	else {
		    		rows.add(ElementAttributesContainer.createTextAttributeRow(element, toolkit, declaration, parent, 2));
		    	}
		}
	}
	
	public static class DetailsTab extends ElementAttributesContainer {
		
		@PostConstruct
		private void createControls(Composite parent) {
			Composite client = super.createSection(parent, 2, RuleMessages.quickfix_title, RuleMessages.quickfix_description);
			CMElementDeclaration ed = modelQuery.getCMElementDeclaration(element);
			if (ed != null) {
				uiDelegate.createControls(client, element, ed, rows);
			}
		}
	}
}

