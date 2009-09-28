/**
 * Copyright (c) 2000-2009 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portlet.journal.action;

import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.security.auth.PrincipalException;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.ServiceContextFactory;
import com.liferay.portal.struts.PortletAction;
import com.liferay.portlet.journal.DuplicateFeedIdException;
import com.liferay.portlet.journal.FeedContentFieldException;
import com.liferay.portlet.journal.FeedDescriptionException;
import com.liferay.portlet.journal.FeedIdException;
import com.liferay.portlet.journal.FeedNameException;
import com.liferay.portlet.journal.FeedTargetLayoutFriendlyUrlException;
import com.liferay.portlet.journal.FeedTargetPortletIdException;
import com.liferay.portlet.journal.NoSuchFeedException;
import com.liferay.portlet.journal.model.JournalFeed;
import com.liferay.portlet.journal.service.JournalFeedServiceUtil;
import com.liferay.util.RSSUtil;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * <a href="EditFeedAction.java.html"><b><i>View Source</i></b></a>
 *
 * @author Raymond Augé
 */
public class EditFeedAction extends PortletAction {

	public void processAction(
			ActionMapping mapping, ActionForm form, PortletConfig portletConfig,
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		String cmd = ParamUtil.getString(actionRequest, Constants.CMD);

		try {
			if (cmd.equals(Constants.ADD) || cmd.equals(Constants.UPDATE)) {
				updateFeed(actionRequest);
			}
			else if (cmd.equals(Constants.DELETE)) {
				deleteFeeds(actionRequest);
			}

			sendRedirect(actionRequest, actionResponse);
		}
		catch (Exception e) {
			if (e instanceof NoSuchFeedException ||
				e instanceof PrincipalException) {

				SessionErrors.add(actionRequest, e.getClass().getName());

				setForward(actionRequest, "portlet.journal.error");
			}
			else if (e instanceof DuplicateFeedIdException ||
					 e instanceof FeedContentFieldException ||
					 e instanceof FeedDescriptionException ||
					 e instanceof FeedIdException ||
					 e instanceof FeedNameException ||
					 e instanceof FeedTargetLayoutFriendlyUrlException ||
					 e instanceof FeedTargetPortletIdException) {

				SessionErrors.add(actionRequest, e.getClass().getName());
			}
			else {
				throw e;
			}
		}
	}

	public ActionForward render(
			ActionMapping mapping, ActionForm form, PortletConfig portletConfig,
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws Exception {

		try {
			String cmd = ParamUtil.getString(renderRequest, Constants.CMD);

			if (!cmd.equals(Constants.ADD)) {
				ActionUtil.getFeed(renderRequest);
			}
		}
		catch (NoSuchFeedException nssfe) {

			// Let this slide because the user can manually input a feed id for
			// a new syndicated feed that does not yet exist.

		}
		catch (Exception e) {
			if (e instanceof PrincipalException) {
				SessionErrors.add(renderRequest, e.getClass().getName());

				return mapping.findForward("portlet.journal.error");
			}
			else {
				throw e;
			}
		}

		return mapping.findForward(
			getForward(renderRequest, "portlet.journal.edit_feed"));
	}

	protected void deleteFeeds(ActionRequest actionRequest) throws Exception {
		long groupId = ParamUtil.getLong(actionRequest, "groupId");

		String[] deleteFeedIds = StringUtil.split(
			ParamUtil.getString(actionRequest, "deleteFeedIds"));

		for (int i = 0; i < deleteFeedIds.length; i++) {
			JournalFeedServiceUtil.deleteFeed(groupId, deleteFeedIds[i]);
		}
	}

	protected void updateFeed(ActionRequest actionRequest) throws Exception {
		String cmd = ParamUtil.getString(actionRequest, Constants.CMD);

		long groupId = ParamUtil.getLong(actionRequest, "groupId");

		String feedId = ParamUtil.getString(actionRequest, "feedId");
		boolean autoFeedId = ParamUtil.getBoolean(actionRequest, "autoFeedId");

		String name = ParamUtil.getString(actionRequest, "name");
		String description = ParamUtil.getString(actionRequest, "description");
		String type = ParamUtil.getString(actionRequest, "type");
		String structureId = ParamUtil.getString(actionRequest, "structureId");
		String templateId = ParamUtil.getString(actionRequest, "templateId");
		String rendererTemplateId = ParamUtil.getString(
			actionRequest, "rendererTemplateId");
		int delta = ParamUtil.getInteger(actionRequest, "delta");
		String orderByCol = ParamUtil.getString(actionRequest, "orderByCol");
		String orderByType = ParamUtil.getString(actionRequest, "orderByType");
		String targetLayoutFriendlyUrl = ParamUtil.getString(
			actionRequest, "targetLayoutFriendlyUrl");
		String targetPortletId = ParamUtil.getString(
			actionRequest, "targetPortletId");
		String contentField = ParamUtil.getString(
			actionRequest, "contentField");

		String feedType = RSSUtil.DEFAULT_TYPE;
		double feedVersion = RSSUtil.DEFAULT_VERSION;

		String feedTypeAndVersion = ParamUtil.getString(
			actionRequest, "feedTypeAndVersion");

		if (Validator.isNotNull(feedTypeAndVersion)) {
			String[] parts = feedTypeAndVersion.split(StringPool.COLON);

			try {
				feedType = parts[0];
				feedVersion = GetterUtil.getDouble(parts[1]);
			}
			catch (Exception e) {
			}
		}
		else {
			feedType = ParamUtil.getString(actionRequest, "feedType", feedType);
			feedVersion = ParamUtil.getDouble(
				actionRequest, "feedVersion", feedVersion);
		}

		ServiceContext serviceContext = ServiceContextFactory.getInstance(
			JournalFeed.class.getName(), actionRequest);

		if (cmd.equals(Constants.ADD)) {

			// Add feed

			JournalFeedServiceUtil.addFeed(
				groupId, feedId, autoFeedId, name, description, type,
				structureId, templateId, rendererTemplateId, delta, orderByCol,
				orderByType, targetLayoutFriendlyUrl, targetPortletId,
				contentField, feedType, feedVersion, serviceContext);
		}
		else {

			// Update feed

			JournalFeedServiceUtil.updateFeed(
				groupId, feedId, name, description, type, structureId,
				templateId, rendererTemplateId, delta, orderByCol, orderByType,
				targetLayoutFriendlyUrl, targetPortletId, contentField,
				feedType, feedVersion, serviceContext);
		}
	}

}