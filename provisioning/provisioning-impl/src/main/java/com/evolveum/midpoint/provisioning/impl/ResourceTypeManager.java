package com.evolveum.midpoint.provisioning.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.evolveum.midpoint.common.QueryUtil;
import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.ucf.api.ResultHandler;
import com.evolveum.midpoint.provisioning.util.ShadowCacheUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.constants.ConnectorTestOperation;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.exception.CommunicationException;
import com.evolveum.midpoint.schema.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.exception.SystemException;
import com.evolveum.midpoint.schema.holder.XPathHolder;
import com.evolveum.midpoint.schema.holder.XPathSegment;
import com.evolveum.midpoint.schema.processor.ComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.Definition;
import com.evolveum.midpoint.schema.processor.ItemDefinition;
import com.evolveum.midpoint.schema.processor.Property;
import com.evolveum.midpoint.schema.processor.ResourceObject;
import com.evolveum.midpoint.schema.processor.ResourceObjectAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObjectDefinition;
import com.evolveum.midpoint.schema.processor.Schema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.DebugUtil;
import com.evolveum.midpoint.schema.util.JAXBUtil;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_1.CapabilitiesType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.QueryType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType.Attributes;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.XmlSchemaType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_1.ActivationCapabilityType;

@Component
public class ResourceTypeManager {

	@Autowired
	@Qualifier("cacheRepositoryService")
	private RepositoryService repositoryService;
	@Autowired
	private ResourceSchemaCache resourceSchemaCache;
	@Autowired
	private ConnectorTypeManager connectorTypeManager;

	private static final Trace LOGGER = TraceManager.getTrace(ResourceTypeManager.class);
	
	public ResourceTypeManager() {
		repositoryService = null;
	}

	/**
	 * Get the value of repositoryService.
	 * 
	 * @return the value of repositoryService
	 */
	public RepositoryService getRepositoryService() {
		return repositoryService;
	}

	/**
	 * Set the value of repositoryService
	 * 
	 * Expected to be injected.
	 * 
	 * @param repositoryService
	 *            new value of repositoryService
	 */
	public void setRepositoryService(RepositoryService repositoryService) {
		this.repositoryService = repositoryService;
	}


	/**
	 * Make sure that the resource is complete.
	 * 
	 * It will check if the resource has a sufficiently fresh schema, etc.
	 * 
	 * Returned resource may be the same or may be a different instance, but it
	 * is guaranteed that it will be "fresher" and will correspond to the
	 * repository state (assuming that the provided resource also corresponded
	 * to the repository state).
	 * 
	 * The connector schema that was fetched before can be supplied to this
	 * method. This is just an optimization. It comes handy e.g. in test
	 * connection case.
	 * 
	 * Note: This is not really the best place for this method. Need to figure
	 * out correct place later.
	 * 
	 * @param resource
	 *            Resource to check
	 * @param resourceSchema
	 *            schema that was freshly pre-fetched (or null)
	 * @param result
	 * 
	 * @return completed resource
	 * @throws ObjectNotFoundException
	 *             connector instance was not found
	 * @throws SchemaException
	 * @throws CommunicationException
	 *             cannot fetch resource schema
	 */
	public ResourceType completeResource(ResourceType resource, Schema resourceSchema, OperationResult result)
			throws ObjectNotFoundException, SchemaException, CommunicationException {

		// Check presence of a schema
		XmlSchemaType xmlSchemaType = resource.getSchema();
		if (xmlSchemaType == null) {
			xmlSchemaType = new XmlSchemaType();
			resource.setSchema(xmlSchemaType);
		}
		Element xsdElement = ObjectTypeUtil.findXsdElement(xmlSchemaType);

		ResourceType newResource = null;

		ConnectorInstance connector = getConnectorInstance(resource, result);

		if (xsdElement == null) {
			// There is no schema, we need to pull it from the resource

			if (resourceSchema == null) { // unless it has been already pulled
				LOGGER.trace("Fetching resource schema for " + ObjectTypeUtil.toShortString(resource));
				try {
					// Fetch schema from connector, UCF will convert it to
					// Schema Processor format and add all
					// necessary annotations
					resourceSchema = connector.getResourceSchema(result);

				} catch (com.evolveum.midpoint.provisioning.ucf.api.CommunicationException ex) {
					throw new CommunicationException("Cannot fetch resource schema: " + ex.getMessage(), ex);
				} catch (GenericFrameworkException ex) {
					throw new GenericConnectorException("Generic error in connector " + connector + ": "
							+ ex.getMessage(), ex);
				}
			}
			LOGGER.debug("Generated resource schema for " + ObjectTypeUtil.toShortString(resource) + ": "
					+ resourceSchema.getDefinitions().size() + " definitions");

			adjustSchemaForCapabilities(resource, resourceSchema);

			Document xsdDoc = null;
			try {
				// Convert to XSD
				LOGGER.trace("Generating XSD resource schema for " + ObjectTypeUtil.toShortString(resource));

				xsdDoc = resourceSchema.serializeToXsd();

			} catch (SchemaException e) {
				throw new SchemaException("Error processing resource schema for "
						+ ObjectTypeUtil.toShortString(resource) + ": " + e.getMessage(), e);
			}
			// Store into repository (modify ResourceType)
			LOGGER.info("Storing generated schema in resource " + ObjectTypeUtil.toShortString(resource));

			xsdElement = DOMUtil.getFirstChildElement(xsdDoc);
			xmlSchemaType.getAny().add(xsdElement);
			xmlSchemaType.setCachingMetadata(MiscSchemaUtil.generateCachingMetadata());

			ObjectModificationType objectModificationType = ObjectTypeUtil.createModificationReplaceProperty(
					resource.getOid(), SchemaConstants.I_SCHEMA, xmlSchemaType);

			repositoryService.modifyObject(resource.getClass(), objectModificationType, result);

			newResource = resourceSchemaCache.put(resource);
		}

		if (newResource == null) {
			// try to fetch schema from cache
			newResource = resourceSchemaCache.get(resource);
		}

		addNativeCapabilities(newResource, connector, result);

		return newResource;
	}

	public void testConnection(ResourceType resourceType, OperationResult parentResult) {

		// === test INITIALIZATION ===

		OperationResult initResult = parentResult
				.createSubresult(ConnectorTestOperation.CONNECTOR_INITIALIZATION.getOperation());
		ConnectorInstance connector;
		try {

			connector = getConnectorInstance(resourceType, initResult);
			initResult.recordSuccess();
		} catch (ObjectNotFoundException e) {
			// The connector was not found. The resource definition is either
			// wrong or the connector is not
			// installed.
			initResult.recordFatalError("The connector was not found", e);
			return;
		} catch (SchemaException e) {
			initResult.recordFatalError("Schema error while dealing with the connector definition", e);
			return;
		} catch (RuntimeException e) {
			initResult.recordFatalError("Unexpected runtime error", e);
			return;
		} catch (CommunicationException e) {
			initResult.recordFatalError("Communication error", e);
			return;
		}
		LOGGER.debug("Testing connection to the resource with oid {}", resourceType.getOid());

		// === test CONFIGURATION ===

		OperationResult configResult = parentResult
				.createSubresult(ConnectorTestOperation.CONFIGURATION_VALIDATION.getOperation());

		try {
			connector.configure(resourceType.getConfiguration(), configResult);
			configResult.recordSuccess();
		} catch (com.evolveum.midpoint.provisioning.ucf.api.CommunicationException e) {
			configResult.recordFatalError("Communication error", e);
			return;
		} catch (GenericFrameworkException e) {
			configResult.recordFatalError("Generic error", e);
			return;
		} catch (SchemaException e) {
			configResult.recordFatalError("Schema error", e);
			return;
		} catch (RuntimeException e) {
			configResult.recordFatalError("Unexpected runtime error", e);
			return;
		}

		// === test CONNECTION ===

		// delegate the main part of the test to the connector
		connector.test(parentResult);

		parentResult.computeStatus();
		if (!parentResult.isAcceptable()) {
			// No point in going on. Following tests will fail anyway, they will
			// just produce misleading
			// messages.
			return;
		}

		// === test SCHEMA ===

		OperationResult schemaResult = parentResult.createSubresult(ConnectorTestOperation.CONNECTOR_SCHEMA
				.getOperation());

		Schema schema = null;
		try {
			// Try to fetch schema from the connector. The UCF will convert it
			// to Schema Processor
			// format, so it is already structured
			schema = connector.getResourceSchema(schemaResult);
		} catch (com.evolveum.midpoint.provisioning.ucf.api.CommunicationException e) {
			schemaResult.recordFatalError("Communication error: " + e.getMessage(), e);
			return;
		} catch (GenericFrameworkException e) {
			schemaResult.recordFatalError("Generic error: " + e.getMessage(), e);
			return;
		}

		if (schema == null || schema.isEmpty()) {
			schemaResult.recordFatalError("Empty schema returned");
			return;
		}

		// Invoke completeResource(). This will store the fetched schema to the
		// ResourceType
		// if there is no <schema> definition already. Therefore the
		// testResource() can be used to
		// generate the resource schema - until we have full schema caching
		// capability.
		try {
			completeResource(resourceType, schema, schemaResult);
		} catch (ObjectNotFoundException e) {
			schemaResult.recordFatalError(
					"Object not found (unexpected error, probably a bug): " + e.getMessage(), e);
			return;
		} catch (SchemaException e) {
			schemaResult.recordFatalError(
					"Schema processing error (probably connector bug): " + e.getMessage(), e);
			return;
		} catch (CommunicationException e) {
			schemaResult.recordFatalError("Communication error: " + e.getMessage(), e);
			return;
		}

		schemaResult.recordSuccess();

		// TODO: connector sanity (e.g. at least one account type, identifiers
		// in schema, etc.)

	}

	/**
	 * Adjust scheme with respect to capabilities. E.g. disable attributes that
	 * are used for special purpose (such as account activation simulation).
	 */
	private void adjustSchemaForCapabilities(ResourceType resource, Schema resourceSchema) {
		if (resource.getCapabilities() == null) {
			return;
		}
		ActivationCapabilityType activationCapability = ResourceTypeUtil.getCapability(resource
				.getCapabilities().getAny(), ActivationCapabilityType.class);
		if (activationCapability != null && activationCapability.getEnableDisable() != null) {
			QName attributeName = activationCapability.getEnableDisable().getAttribute();
			if (attributeName != null) {
				// The attribute used for enable/disable simulation should be
				// ignored in the schema
				// otherwise strange things may happen, such as changing the
				// same attribute both from
				// activation/enable and from the attribute using its native
				// name.
				ResourceObjectDefinition accountDefinition = resourceSchema.findAccountDefinition();
				ResourceObjectAttributeDefinition attributeDefinition = accountDefinition
						.findAttributeDefinition(attributeName);
				if (attributeDefinition != null) {
					attributeDefinition.setIgnored(true);
				} else {
					// simulated activation attribute points to something that
					// is not in the schema
					// technically, this is an error. But it looks to be quite
					// common in connectors.
					// The enable/disable is using operational attributes that
					// are not exposed in the
					// schema, but they work if passed to the connector.
					// Therefore we don't want to break anything. We could log
					// an warning here, but the
					// warning would be quite frequent. Maybe a better place to
					// warn user would be import
					// of the object.
					LOGGER.debug("Simulated activation attribute "
							+ attributeName
							+ " in "
							+ ObjectTypeUtil.toShortString(resource)
							+ " does not exist in the resource schema. This may work well, but it is not clean. Connector exposing such schema should be fixed.");
				}
			}
		}
	}

	public Schema getResourceSchema(ResourceType resource, ConnectorInstance connector,
			OperationResult parentResult) throws SchemaException, CommunicationException {

		Schema schema = null;
		try {

			// Make sure that the schema is retrieved from the resource
			// this will also retrieve the schema from cache and/or parse it if
			// needed
			ResourceType completeResource = completeResource(resource, null, parentResult);
			schema = RefinedResourceSchema.getResourceSchema(completeResource);

		} catch (SchemaException e) {
			parentResult.recordFatalError("Unable to parse resource schema: " + e.getMessage(), e);
			throw new SchemaException("Unable to parse resource schema: " + e.getMessage(), e);
		} catch (ObjectNotFoundException e) {
			// this really should not happen
			parentResult.recordFatalError("Unexpected ObjectNotFoundException: " + e.getMessage(), e);
			throw new SystemException("Unexpected ObjectNotFoundException: " + e.getMessage(), e);
		}

		checkSchema(schema);

		return schema;
	}

	public void listShadows(final ResourceType resource, final QName objectClass,
			final ShadowHandler handler, final boolean readFromRepository, final OperationResult parentResult)
			throws CommunicationException, ObjectNotFoundException, SchemaException {

		Validate.notNull(objectClass);
		if (resource == null) {
			parentResult.recordFatalError("Resource must not be null");
			throw new IllegalArgumentException("Resource must not be null.");
		}

		LOGGER.trace("Start listing objects on resource with oid {} with object class {} ",
				resource.getOid(), objectClass);

		ConnectorInstance connector = getConnectorInstance(resource, parentResult);

		Schema schema = getResourceSchema(resource, connector, parentResult);

		if (schema == null) {
			parentResult.recordFatalError("Can't get resource schema.");
			throw new IllegalArgumentException("Can't get resource schema.");
		}

		ResourceObjectDefinition resourceDef = (ResourceObjectDefinition) schema
				.findContainerDefinitionByType(objectClass);

		if (resourceDef == null) {
			// Unknown objectclass
			SchemaException ex = new SchemaException("Object class " + objectClass
					+ " defined in the repository shadow is not known in schema of resource "
					+ ObjectTypeUtil.toShortString(resource));
			parentResult.recordFatalError("Object class " + objectClass
					+ " defined in the repository shadow is not known in resource schema", ex);
			throw ex;
		}

		ResultHandler resultHandler = new ResultHandler() {

			@Override
			public boolean handle(ResourceObject object) {

				ResourceObjectShadowType shadow;
				if (readFromRepository) {
					// Attached shadow (with OID)
					try {
						shadow = lookupShadow(object, resource, parentResult);
					} catch (SchemaException e) {
						// TODO: better error handling
						LOGGER.error(
								"Schema exception in resource object search on {} for {}: {}",
								new Object[] { ObjectTypeUtil.toShortString(resource), objectClass,
										e.getMessage(), e });
						return false;
					}
				} else {
					// Detached shadow (without OID)
					try {
						shadow = assembleShadow(object, null, parentResult);

					} catch (SchemaException e) {
						// TODO: better error handling
						LOGGER.error(
								"Schema exception in resource object search on {} for {}: {}",
								new Object[] { ObjectTypeUtil.toShortString(resource), objectClass,
										e.getMessage(), e });
						return false;
					}
				}

				// TODO: if shadow does not exists, create it now

				return handler.handle(shadow);
			}
		};

		try {
			connector.search(resourceDef, resultHandler, parentResult);
			LOGGER.trace("Finished listing obejcts.");
		} catch (com.evolveum.midpoint.provisioning.ucf.api.CommunicationException e) {
			parentResult.recordFatalError("Error communicationg with the connector " + connector
					+ ". Reason: " + e.getMessage(), e);
			throw new CommunicationException("Error communicationg with the connector " + connector
					+ ". Reason: " + e.getMessage(), e);
		} catch (GenericFrameworkException e) {
			parentResult.recordFatalError("Generic error in connector. Reason: " + e.getMessage(), e);
			throw new GenericConnectorException("Generic error in connector. Reason: " + e.getMessage(), e);
		}
		parentResult.recordSuccess();
	}

	public void searchObjectsIterative(final QName objectClass, final ResourceType resourceType,
			final ShadowHandler handler, final DiscoveryHandler discoveryHandler,
			final OperationResult parentResult) throws ObjectNotFoundException, CommunicationException,
			SchemaException {

		Validate.notNull(resourceType, "Resource must not be null.");
		Validate.notNull(objectClass, "Object class must not be null.");
		Validate.notNull(parentResult, "Operation result must not be null.");

		LOGGER.trace("Searching objects iterative with obejct class {}, resource: {}.", objectClass,
				ObjectTypeUtil.toShortString(resourceType));

		ConnectorInstance connector = getConnectorInstance(resourceType, parentResult);

		final Schema schema = getResourceSchema(resourceType, connector, parentResult);

		if (schema == null) {
			parentResult.recordFatalError("Can't get resource schema.");
			throw new IllegalArgumentException("Can't get resource schema.");
		}

		ResourceObjectDefinition resourceDef = (ResourceObjectDefinition) schema
				.findContainerDefinitionByType(objectClass);

		if (resourceDef == null) {
			String message = "Object class " + objectClass + " is not defined in schema of "
					+ ObjectTypeUtil.toShortString(resourceType);
			LOGGER.error(message);
			parentResult.recordFatalError(message);
			throw new SchemaException(message);
		}

		ResultHandler resultHandler = new ResultHandler() {

			@Override
			public boolean handle(ResourceObject object) {
				ResourceObjectShadowType shadow;
				LOGGER.trace("Found resource object {}", DebugUtil.prettyPrint(object));
				try {

					// Try to find shadow that corresponds to the resource
					// object
					shadow = lookupShadow(object, resourceType, parentResult);

					if (shadow == null) {
						LOGGER.trace(
								"Shadow object (in repo) corresponding to the resource object (on the resource) was not found. The repo shadow will be created. The resource object:\n{}",
								DebugUtil.prettyPrint(object));

						// TODO: make sure that the resource object has
						// appropriate definition
						// (use objectClass and schema)

						// The resource object obviously exists on the resource,
						// but appropriate shadow does not exist in the
						// repository
						// we need to create the shadow to align repo state to
						// the reality (resource)
						shadow = ShadowCacheUtil.createShadow(object, resourceType, null);
						try {
							ResourceObjectShadowType repoShadow = ShadowCacheUtil.createRepositoryShadow(object, resourceType, shadow);
							String oid = getRepositoryService().addObject(repoShadow, parentResult);
							shadow.setOid(oid);
						} catch (ObjectAlreadyExistsException e) {
							// This should not happen. We haven't supplied an
							// OID so is should not conflict
							LOGGER.error("Unexpected repository behavior: Object already exists: {}",
									e.getMessage(), e);
							// but still go on ...
						}

						// And notify about the change we have discovered (if
						// requested to do so)
						if (discoveryHandler != null) {
							discoveryHandler.discovered(shadow, parentResult);
						}
					} else {
						LOGGER.trace("Found shadow object in the repository {}",
								DebugUtil.prettyPrint(shadow));
					}

				} catch (SchemaException e) {
					// TODO: better error handling
					parentResult.recordFatalError("Schema error: " + e.getMessage(), e);
					LOGGER.error("Schema error: {}", e.getMessage(), e);
					return false;
				}

				// TODO: if shadow does not exists, create it now

				return handler.handle(shadow);
			}

		};

		try {

			connector.search(resourceDef, resultHandler, parentResult);
		} catch (GenericFrameworkException e) {
			parentResult.recordFatalError("Generic error in the connector: " + e.getMessage(), e);
			throw new CommunicationException("Generic error in the connector: " + e.getMessage(), e);

		} catch (com.evolveum.midpoint.provisioning.ucf.api.CommunicationException ex) {
			parentResult.recordFatalError(
					"Error communicating with the connector " + connector + ": " + ex.getMessage(), ex);
			throw new CommunicationException("Error communicating with the connector " + connector + ": "
					+ ex.getMessage(), ex);
		}

		parentResult.recordSuccess();

	}

	/**
	 * Locates the appropriate Shadow in repository that corresponds to the
	 * provided resource object.
	 * 
	 * @param parentResult
	 * 
	 * @return current unchanged shadow object that corresponds to provided
	 *         resource object or null if the object does not exist
	 * @throws SchemaProcessorException
	 * @throws SchemaException
	 */
	private ResourceObjectShadowType lookupShadow(ResourceObject resourceObject, ResourceType resource,
			OperationResult parentResult) throws SchemaException {

		QueryType query = ShadowCacheUtil.createSearchShadowQuery(resourceObject, resource, parentResult);
		PagingType paging = new PagingType();

		// TODO: check for errors
		List<ResourceObjectShadowType> results;

		results = getRepositoryService().searchObjects(ResourceObjectShadowType.class, query, paging,
				parentResult);

		LOGGER.trace("lookupShadow found {} objects", results.size());

		if (results.size() == 0) {
			return null;
		}
		if (results.size() > 1) {
			LOGGER.error("More than one shadows found for " + resourceObject);
			// TODO: Better error handling later
			throw new IllegalStateException("More than one shadows found for " + resourceObject);
		}

		ResourceObjectShadowType repoShadow = results.get(0);
		return ShadowCacheUtil.createShadow(resourceObject, resource, repoShadow);
	}

	/**
	 * Creates a shadow object from the supplied resource object.
	 * 
	 * If an optional resourceObject is specified, it will be used as a base for
	 * creating the shadow. In this case the same instance is returned, but it
	 * is enriched with attributes from the resource object.
	 * 
	 * @param resourceObject
	 * @param repositoryShadow
	 * @return
	 * @throws SchemaException
	 */
	public ResourceObjectShadowType assembleShadow(ResourceObject resourceObject,
			ResourceObjectShadowType repositoryShadow, OperationResult parentResult) throws SchemaException {
		ResourceObjectShadowType resultShadow;
		Document doc;
		if (repositoryShadow != null) {
			resultShadow = repositoryShadow;
			Object firstElement = resultShadow.getAttributes().getAny().get(0);
			doc = JAXBUtil.getDocument(firstElement);
		} else {
			// TODO: create specific subtypes
			resultShadow = new ResourceObjectShadowType();
			Attributes attributes = new Attributes();
			resultShadow.setAttributes(attributes);
			doc = DOMUtil.getDocument();
		}
		// Let's replace the attribute values fetched from repository with the
		// ResourceObject content fetched from resource. The resource is more
		// fresh and the attributes more complete.
		// TODO: Discovery
		// TODO: Optimize the use of XML namespaces

		List<Object> xmlAttributes;
		try {
			xmlAttributes = resourceObject.serializePropertiesToJaxb(doc);

		} catch (SchemaException ex) {
			parentResult.recordFatalError(ex.getMessage());
			throw ex;
		}
		resultShadow.getAttributes().getAny().clear();
		resultShadow.getAttributes().getAny().addAll(xmlAttributes);

		return resultShadow;
	}

	private void checkSchema(Schema schema) throws SchemaException {
		// This is resource schema, it should contain only
		// ResourceObjectDefintions
		for (Definition def : schema.getDefinitions()) {
			if (def instanceof ComplexTypeDefinition) {
				// This is OK
			} else if (def instanceof ResourceObjectDefinition) {
				checkResourceObjectDefinition((ResourceObjectDefinition) def);
			} else {
				throw new SchemaException("Unexpected definition in resource schema: " + def);
			}
		}
	}

	private void checkResourceObjectDefinition(ResourceObjectDefinition rod) throws SchemaException {
		for (ItemDefinition def : rod.getDefinitions()) {
			if (!(def instanceof ResourceObjectAttributeDefinition)) {
				throw new SchemaException("Unexpected definition in resource schema object " + rod + ": "
						+ def);
			}
		}
	}

	private void addNativeCapabilities(ResourceType resource, ConnectorInstance connector,
			OperationResult result) throws CommunicationException {
		Set<Object> capabilities = null;
		try {

			capabilities = connector.getCapabilities(result);

		} catch (com.evolveum.midpoint.provisioning.ucf.api.CommunicationException ex) {
			throw new CommunicationException("Cannot fetch resource schema: " + ex.getMessage(), ex);
		} catch (GenericFrameworkException ex) {
			throw new GenericConnectorException("Generic error in connector " + connector + ": "
					+ ex.getMessage(), ex);
		}

		CapabilitiesType capType = new CapabilitiesType();
		capType.getAny().addAll(capabilities);
		resource.setNativeCapabilities(capType);
	}

	/**
	 * @param resource
	 * @param parentResult
	 * @return
	 * @throws SchemaException
	 * @throws ObjectNotFoundException
	 * @throws CommunicationException
	 */
	private ConnectorInstance getConnectorInstance(ResourceType resource, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException, CommunicationException {
		return connectorTypeManager.getConfiguredConnectorInstance(resource, parentResult);
	}

}
