/*
 * Copyright 2012 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.services.task.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jbpm.services.task.events.TaskEventSupport;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.kie.api.task.model.Content;
import org.kie.api.task.model.Task;
import org.kie.internal.task.api.ContentMarshallerContext;
import org.kie.internal.task.api.TaskContentService;
import org.kie.internal.task.api.TaskModelProvider;
import org.kie.internal.task.api.TaskPersistenceContext;
import org.kie.internal.task.api.model.ContentData;
import org.kie.internal.task.api.model.InternalContent;
import org.kie.internal.task.api.model.InternalTaskData;

/**
 *
 */
public class TaskContentServiceImpl implements TaskContentService {

    private TaskPersistenceContext persistenceContext;
    private TaskEventSupport taskEventSupport;
    
    private org.kie.internal.task.api.TaskContext context;

    public TaskContentServiceImpl() {
    }
    
    public TaskContentServiceImpl(org.kie.internal.task.api.TaskContext context, TaskPersistenceContext persistenceContext, TaskEventSupport taskEventSupport) {
    	this.context = context;    	
        this.persistenceContext = persistenceContext;
    	this.taskEventSupport = taskEventSupport;
    }

    public void setPersistenceContext(TaskPersistenceContext persistenceContext) {
        this.persistenceContext = persistenceContext;
    }
    
    public void setTaskEventSupport(TaskEventSupport taskEventSupport) {
        this.taskEventSupport = taskEventSupport;
    }
    
    @SuppressWarnings("unchecked")
	public long addOutputContent(long taskId, Map<String, Object> params) {
        Task task = persistenceContext.findTask(taskId);
        long outputContentId = task.getTaskData().getOutputContentId();
        Content outputContent = persistenceContext.findContent(outputContentId);

        long contentId = -1;
        if (outputContent == null) { 
            ContentMarshallerContext context = getMarshallerContext(task);
            ContentData outputContentData = ContentMarshallerHelper.marshal(params, context.getEnvironment());
            Content content = TaskModelProvider.getFactory().newContent();
            ((InternalContent) content).setContent(outputContentData.getContent());
            persistenceContext.persistContent(content);
            
            ((InternalTaskData) task.getTaskData()).setOutput(content.getId(), outputContentData);
            contentId = content.getId();
        } else {
            // I need to merge it if it already exist
            ContentMarshallerContext context = getMarshallerContext(task);
            Object unmarshalledObject = ContentMarshallerHelper.unmarshall(outputContent.getContent(), context.getEnvironment(), context.getClassloader());
            if(unmarshalledObject != null && unmarshalledObject instanceof Map){
                ((Map<String, Object>)unmarshalledObject).putAll(params);
            }
            ContentData outputContentData = ContentMarshallerHelper.marshal(unmarshalledObject, context.getEnvironment());
            ((InternalContent)outputContent).setContent(outputContentData.getContent());
            persistenceContext.persistContent(outputContent);
            contentId = outputContentId;
        }
        
        taskEventSupport.fireAfterTaskOutputVariablesChanged(task, context, params);
        
        return contentId;
    }

    // TODO: if there's an existing document content entity, we lose all link to that through this!
    public long setDocumentContent(long taskId, Content content) {
        Task task = persistenceContext.findTask(taskId);
        persistenceContext.persistContent(content);
        ((InternalTaskData) task.getTaskData()).setDocumentContentId(content.getId());
        return content.getId();
    }

    public void deleteDocumentContent(long taskId, long contentId) {
        Task task = persistenceContext.findTask(taskId);
        ((InternalTaskData) task.getTaskData()).setDocumentContentId(-1);
        Content content = persistenceContext.findContent(contentId);
        persistenceContext.removeContent(content);

    }

    public List<Content> getAllContentByTaskId(long taskId) {
    	Task task = persistenceContext.findTask(taskId);
    	
    	long inputContentId = task.getTaskData().getDocumentContentId();
    	long outputContentId = task.getTaskData().getOutputContentId();
    	long faultContentId = task.getTaskData().getFaultContentId();
    	
    	List<Content> allContent = new ArrayList<Content>();
    	
    	allContent.add(persistenceContext.findContent(inputContentId));
    	allContent.add(persistenceContext.findContent(outputContentId));
    	allContent.add(persistenceContext.findContent(faultContentId));
    	
        return allContent;
    }

    public Content getContentById(long contentId) {
        return persistenceContext.findContent(contentId);
    }
    
    @Override
    public void addMarshallerContext(String ownerId, ContentMarshallerContext context) {
        TaskContentRegistry.get().addMarshallerContext(ownerId, context);
    }

    @Override
    public void removeMarshallerContext(String ownerId) {
    	TaskContentRegistry.get().removeMarshallerContext(ownerId);
    }   

    public ContentMarshallerContext getMarshallerContext(Task task) {
        return TaskContentRegistry.get().getMarshallerContext(task);
    }
}
