/**
 * Copyright (c) 2010-2013, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.config;

import java.util.ArrayList;
import java.util.List;

import org.openhab.binding.zwave.internal.protocol.ConfigurationParameter;
import org.openhab.binding.zwave.internal.protocol.ZWaveController;
import org.openhab.binding.zwave.internal.protocol.ZWaveEventListener;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass.CommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveConfigurationCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveWakeUpCommandClass;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveAssociationEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveEvent;
import org.openhab.binding.zwave.internal.protocol.initialization.ZWaveNodeSerializer;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Z Wave configuration class Interfaces between the REST services using the
 * OpenHABConfigurationService interface. It uses the ZWave product database to
 * configure zwave devices.
 * 
 * @author Chris Jackson
 * @since 1.4.0
 * 
 */
public class ZWaveConfiguration implements OpenHABConfigurationService, ZWaveEventListener {
	private static final Logger logger = LoggerFactory.getLogger(ZWaveConfiguration.class);

	private ZWaveController zController = null;

	public ZWaveConfiguration() {
	}

	/**
	 * Constructor for the configuration class. Sets the zwave controller
	 * which is required in order to allow the class to retrieve the configuration.
	 * @param controller The zWave controller
	 */
	public ZWaveConfiguration(ZWaveController controller) {
		this.zController = controller;

		// Register the service
		FrameworkUtil.getBundle(getClass()).getBundleContext()
				.registerService(OpenHABConfigurationService.class.getName(), this, null);
	}

	@Override
	public String getBundleName() {
		return "zwave";
	}

	@Override
	public String getVersion() {
		return FrameworkUtil.getBundle(getClass()).getBundleContext().getBundle().getVersion().toString();
	}

	@Override
	public List<OpenHABConfigurationRecord> getConfiguration(String domain) {
		// We only deal with top level domains here!
		if (domain.endsWith("/") == false) {
			logger.error("Malformed domain request in getConfiguration '{}'", domain);
			return null;
		}

		List<OpenHABConfigurationRecord> records = new ArrayList<OpenHABConfigurationRecord>();
		OpenHABConfigurationRecord record;
		ZWaveNode node;

		if (domain.equals("status/")) {
			// Return the z-wave status information

			return null;
		}
		if (domain.startsWith("products/")) {
			ZWaveProductDatabase database = new ZWaveProductDatabase();

			String[] splitDomain = domain.split("/");

			switch (splitDomain.length) {
			case 1:
				// Just list the manufacturers
				for (ZWaveDbManufacturer manufacturer : database.GetManufacturers()) {
					record = new OpenHABConfigurationRecord(domain + manufacturer.Id.toString() + "/", manufacturer.Name);

					records.add(record);
				}
				break;
			case 2:
				// Get products list
				if (database.FindManufacturer(Integer.parseInt(splitDomain[1])) == false)
					break;

				record = new OpenHABConfigurationRecord(domain, "ManufacturerID", "Manufacturer ID", true);
				record.value = database.getManufacturerId().toString();
				records.add(record);
				
				for (ZWaveDbProduct product : database.GetProducts()) {
					record = new OpenHABConfigurationRecord(domain + product.Reference.get(0).Type + "/" + product.Reference.get(0).Id + "/", product.Model);
					record.value = database.getLabel(product.Label);
					records.add(record);
				}
				break;
			case 4:
				// Get product
				if (database.FindProduct(Integer.parseInt(splitDomain[1]), Integer.parseInt(splitDomain[2]), Integer.parseInt(splitDomain[3])) == false)
					break;

				if(database.doesProductImplementCommandClass(ZWaveCommandClass.CommandClass.CONFIGURATION.getKey()) == true) {
					record = new OpenHABConfigurationRecord(domain + "parameters/", "Configuration Parameters");
					records.add(record);
				}

				if(database.doesProductImplementCommandClass(ZWaveCommandClass.CommandClass.ASSOCIATION.getKey()) == true) {
					record = new OpenHABConfigurationRecord(domain + "associations/", "Association Groups");
					records.add(record);
				}

				record = new OpenHABConfigurationRecord(domain + "classes/", "Command Classes");
				records.add(record);
				break;
			case 5:
				// Get product
				if (database.FindProduct(Integer.parseInt(splitDomain[1]), Integer.parseInt(splitDomain[2]), Integer.parseInt(splitDomain[3])) == false)
					break;

				if (splitDomain[4].equals("parameters")) {
					List<ZWaveDbConfigurationParameter> configList = database.getProductConfigParameters();
					// Loop through the parameters and add to the records...
					for (ZWaveDbConfigurationParameter parameter : configList) {
						record = new OpenHABConfigurationRecord(domain, "configuration" + parameter.Index,
								database.getLabel(parameter.Label), true);

						if (parameter != null)
							record.value = parameter.Default;

						// Add the data type
						if (parameter.Type.equalsIgnoreCase("list")) {
							record.type = OpenHABConfigurationRecord.TYPE.LIST;

							for (ZWaveDbConfigurationListItem item : parameter.Item)
								record.addValue(Integer.toString(item.Value), database.getLabel(item.Label));
						} else if (parameter.Type.equalsIgnoreCase("byte"))
							record.type = OpenHABConfigurationRecord.TYPE.BYTE;
						else
							record.type = OpenHABConfigurationRecord.TYPE.SHORT;

						// Add the description
						record.description = database.getLabel(parameter.Help);

						records.add(record);
					}
				}
				if (splitDomain[4].equals("associations")) {
					List<ZWaveDbAssociationGroup> groupList = database.getProductAssociationGroups();

					if (groupList != null) {
						// Loop through the associations and add to the
						// records...
						for (ZWaveDbAssociationGroup group : groupList) {
							record = new OpenHABConfigurationRecord(domain, "association" + group.Index,
									database.getLabel(group.Label), true);

							// Add the description
							record.description = database.getLabel(group.Help);

							records.add(record);
						}
					}
				}
				if (splitDomain[4].equals("classes")) {
					List<ZWaveDbCommandClass> classList = database.getProductCommandClasses();

					if (classList != null) {
						// Loop through the associations and add to the
						// records...
						for (ZWaveDbCommandClass iClass : classList) {
							record = new OpenHABConfigurationRecord(domain, "class" + iClass.Id,
									ZWaveCommandClass.CommandClass.getCommandClass(iClass.Id).getLabel(), true);
							if(ZWaveCommandClass.CommandClass.getCommandClass(iClass.Id).getCommandClassClass() == null) {
//							if(ccc == null) {
								record.state = OpenHABConfigurationRecord.STATE.WARNING;
							}
							records.add(record);
						}
					}					
				}
				break;
			}
			return records;
		}

		// All domains after here must have an initialised ZWave network
		if (zController == null) {
			logger.error("Controller not initialised in call to getConfiguration");
			return null;
		}
		
		if (domain.equals("nodes/")) {
			ZWaveProductDatabase database = new ZWaveProductDatabase();
			// Return the list of nodes
			for (int nodeId = 0; nodeId < 256; nodeId++) {
				node = zController.getNode(nodeId);
				if (node == null)
					continue;

				logger.debug("Config requested for node {}", nodeId);

				if (node.getName() == null || node.getName().isEmpty()) {
					record = new OpenHABConfigurationRecord("nodes/" + "node" + nodeId + "/", "Node " + nodeId);
				}
				else {
					record = new OpenHABConfigurationRecord("nodes/" + "node" + nodeId + "/", node.getName());
				}
				
				// If we can't find the product, then try and find just the
				// manufacturer
				if (node.getManufacturer() == 0) {
				} else if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) == false) {
					if (database.FindManufacturer(node.getManufacturer()) == false) {
						record.value = "Manufacturer:" + node.getManufacturer() + " [ID:"
								+ Integer.toHexString(node.getDeviceId()) + ",Type:"
								+ Integer.toHexString(node.getDeviceType()) + "]";
					} else {
						record.value = database.getManufacturerName() + " [ID:"
								+ Integer.toHexString(node.getDeviceId()) + ",Type:"
								+ Integer.toHexString(node.getDeviceType()) + "]";
					}
					logger.debug("No database entry node {}: {}", nodeId, record.value);
				} else {
					if (node.getLocation() == null || node.getLocation().isEmpty())
						record.value = database.getProductName();
					else
						record.value = database.getProductName() + ": " + node.getLocation();
				}
				
				// Set the state
				switch(node.getNodeStage()) {
				case DEAD:
					record.state = OpenHABConfigurationRecord.STATE.ERROR;
					break;
				case DONE:
					record.state = OpenHABConfigurationRecord.STATE.OK;
					break;
				default:
					record.state = OpenHABConfigurationRecord.STATE.INITIALIZING;
					break;
				}

				// Add the save button
				record.addAction("Save", "Save Node");
				records.add(record);
			}
			return records;
		}
		if (domain.startsWith("nodes/node")) {
			String nodeNumber = domain.substring(10);
			int next = nodeNumber.indexOf('/');
			String arg = null;
			if (next != -1) {
				arg = nodeNumber.substring(next + 1);
				nodeNumber = nodeNumber.substring(0, next);
			}
			int nodeId = Integer.parseInt(nodeNumber);

			// Return the detailed configuration for this node
			node = zController.getNode(nodeId);
			if (node == null)
				return null;

			// Open the product database
			ZWaveProductDatabase database = new ZWaveProductDatabase();

			// Process the request
			if (arg.equals("")) {
				record = new OpenHABConfigurationRecord(domain, "Name", "Name", false);
				record.value = node.getName();
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "Location", "Location", false);
				record.value = node.getLocation();
				records.add(record);

				if (database.FindManufacturer(node.getManufacturer()) == false) {
					record = new OpenHABConfigurationRecord(domain, "ManufacturerID", "Manufacturer ID", true);
					record.value = Integer.toString(node.getManufacturer());
					records.add(record);
				} else {
					record = new OpenHABConfigurationRecord(domain, "Manufacturer", "Manufacturer", true);
					record.value = database.getManufacturerName();
					records.add(record);
				}

				if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) == false) {
					record = new OpenHABConfigurationRecord(domain, "DeviceId", "Device ID", true);
					record.value = Integer.toString(node.getDeviceId());
					records.add(record);

					record = new OpenHABConfigurationRecord(domain, "DeviceType", "Device Type", true);
					record.value = Integer.toString(node.getDeviceType());
					records.add(record);
					
					record = new OpenHABConfigurationRecord(domain, "Version", "Version", true);
					record.value = Integer.toString(node.getVersion());
					records.add(record);
				} else {
					record = new OpenHABConfigurationRecord(domain, "Product", "Product", true);
					record.value = database.getProductName();
					records.add(record);

					// Add links to configuration if the node supports the various command classes
					if(database.doesProductImplementCommandClass(ZWaveCommandClass.CommandClass.CONFIGURATION.getKey()) == true) {
						record = new OpenHABConfigurationRecord(domain + "parameters/", "Configuration Parameters");
						record.addAction("Refresh", "Refresh");
						records.add(record);
					}

					if(database.doesProductImplementCommandClass(ZWaveCommandClass.CommandClass.ASSOCIATION.getKey()) == true) {
						record = new OpenHABConfigurationRecord(domain + "associations/", "Association Groups");
						record.addAction("Refresh", "Refresh");
						records.add(record);
					}

					if(database.doesProductImplementCommandClass(ZWaveCommandClass.CommandClass.WAKE_UP.getKey()) == true) {
						record = new OpenHABConfigurationRecord(domain + "wakeup/", "Wakeup Period");
						record.addAction("Refresh", "Refresh");
						records.add(record);
					}
				}
				
				record = new OpenHABConfigurationRecord(domain + "status/", "Status");
				records.add(record);
			} else if (arg.equals("status/")) {
				record = new OpenHABConfigurationRecord(domain, "NodeStage", "Node Stage", true);
				record.value = node.getNodeStage().getLabel();
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "NodeStageTime", "Node Stage Time", true);
				record.value = node.getQueryStageTimeStamp().toString();
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "LastUpdated", "Last Updated", true);
				record.value = node.getLastUpdated().toString();
				records.add(record);
			} else if (arg.equals("parameters/")) {
				if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) != false) {
					List<ZWaveDbConfigurationParameter> configList = database.getProductConfigParameters();

					// Get the configuration command class for this node
					ZWaveConfigurationCommandClass configurationCommandClass = (ZWaveConfigurationCommandClass) node
							.getCommandClass(CommandClass.CONFIGURATION);

					if (configurationCommandClass == null) {
						logger.error("Error getting configurationCommandClass for node '{}'", nodeId);
						return null;
					}

					// Loop through the parameters and add to the records...
					for (ZWaveDbConfigurationParameter parameter : configList) {
						record = new OpenHABConfigurationRecord(domain, "configuration" + parameter.Index,
								database.getLabel(parameter.Label), false);

						ConfigurationParameter configurationParameter = configurationCommandClass
								.getParameter(parameter.Index);

						// Only provide a value if it's stored in the node
						// This is the only way we can be sure of its real value
						if (configurationParameter != null)
							record.value = Integer.toString(configurationParameter.getValue());

						// Add the data type
						if (parameter.Type.equalsIgnoreCase("list")) {
							record.type = OpenHABConfigurationRecord.TYPE.LIST;

							for (ZWaveDbConfigurationListItem item : parameter.Item)
								record.addValue(Integer.toString(item.Value), database.getLabel(item.Label));
						} else if (parameter.Type.equalsIgnoreCase("byte"))
							record.type = OpenHABConfigurationRecord.TYPE.BYTE;
						else
							record.type = OpenHABConfigurationRecord.TYPE.SHORT;
						
						// Add any limits
						if(record.type == OpenHABConfigurationRecord.TYPE.LIST) {
							record.minimum = parameter.Minimum;
							record.maximum = parameter.Maximum;
						}

						// Add the description
						record.description = database.getLabel(parameter.Help);

						records.add(record);
					}
				}
			} else if (arg.equals("associations/")) {
				if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) != false) {
					List<ZWaveDbAssociationGroup> groupList = database.getProductAssociationGroups();

					if (groupList != null) {
						// Loop through the associations and add to the
						// records...
						for (ZWaveDbAssociationGroup group : groupList) {
							// Controller reporting associations are set to read
							// only
							record = new OpenHABConfigurationRecord(domain, "association" + group.Index + "/",
									database.getLabel(group.Label), group.SetToController);

							// Add the description
							record.description = database.getLabel(group.Help);

							// Add the action for refresh
							record.addAction("Refresh", "Refresh");

							records.add(record);
						}
					}
				}
			} else if (arg.startsWith("associations/association")) {
				if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) != false) {

					String groupString = arg.substring(24);
					int nextDelimiter = groupString.indexOf('/');
					// String arg = null;
					if (nextDelimiter != -1) {
						// arg = nodeNumber.substring(nextDelimiter + 1);
						groupString = groupString.substring(0, nextDelimiter);
					}
					int groupId = Integer.parseInt(groupString);

					// Get the requested group so we have access to the
					// attributes
					List<ZWaveDbAssociationGroup> groupList = database.getProductAssociationGroups();
					if (groupList == null)
						return null;
					ZWaveDbAssociationGroup group = null;
					for (int cnt = 0; cnt < groupList.size(); cnt++) {
						if (groupList.get(cnt).Index == groupId) {
							group = groupList.get(cnt);
							break;
						}
					}

					if (group == null)
						return null;

					// Get the group members
					List<Integer> members = node.configAssociationGetMembers(groupId);

					for (int id = 0; id < 256; id++) {
						node = zController.getNode(id);
						if (node == null)
							continue;

						if (node.getName() == null || node.getName().isEmpty())
							record = new OpenHABConfigurationRecord(domain, "node" + id, "Node " + id, false);
						else
							record = new OpenHABConfigurationRecord(domain, "node" + id, node.getName(), false);

						record.type = OpenHABConfigurationRecord.TYPE.LIST;
						record.addValue("true", "Member");
						record.addValue("false", "Non-Member");

						if (members != null && members.contains(id)) {
							record.value = "true";
						} else {
							record.value = "false";
						}

						records.add(record);
					}
				}
			}
			else if (arg.equals("wakeup/")) {
				ZWaveWakeUpCommandClass wakeupCommandClass = (ZWaveWakeUpCommandClass) node
						.getCommandClass(CommandClass.WAKE_UP);

				if(wakeupCommandClass == null) {
					logger.error("Error getting wakeupCommandClass for node '{}'", nodeId);
					return null;
				}

				// Display the wakeup parameters.
				// Note that only the interval is writable.
				record = new OpenHABConfigurationRecord(domain, "Interval", "Wakeup Interval", false);
				record.minimum = wakeupCommandClass.getMinInterval();
				record.maximum = wakeupCommandClass.getMaxInterval();
				record.value = Integer.toString(wakeupCommandClass.getInterval());
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "Minimum", "Minimum Interval", true);
				record.value = Integer.toString(wakeupCommandClass.getMinInterval());
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "Maximum", "Maximum Interval", true);
				record.value = Integer.toString(wakeupCommandClass.getMaxInterval());
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "Default", "Default Interval", true);
				record.value = Integer.toString(wakeupCommandClass.getDefaultInterval());
				records.add(record);

				record = new OpenHABConfigurationRecord(domain, "Step", "Interval Step", true);
				record.value = Integer.toString(wakeupCommandClass.getIntervalStep());
				records.add(record);
			}
			
			return records;
		}

		return null;
	}

	@Override
	public void setConfiguration(String domain, List<OpenHABConfigurationRecord> records) {

	}

	@Override
	public String getCommonName() {
		return "ZWave";
	}

	@Override
	public String getDescription() {
		return "Provides interface to Z-Wave network";
	}

	@Override
	public void doAction(String domain, String action) {
		logger.debug("doAction domain '{}' to '{}'", domain, action);

		String[] splitDomain = domain.split("/");

		// There must be at least 2 components to the domain
		if (splitDomain.length < 2) {
			logger.error("Error malformed domain in doAction '{}'", domain);
			return;
		}

		if (splitDomain[0].equals("nodes")) {
			int nodeId = Integer.parseInt(splitDomain[1].substring(4));

			// Get the node - if it exists
			ZWaveNode node = zController.getNode(nodeId);
			if (node == null) {
				logger.error("Error finding node in doAction '{}'", nodeId);
				return;
			}

			if (splitDomain.length == 2) {
				if (action.equals("Save")) {
					logger.debug("Saving node '{}'", nodeId);

					// Write the node to disk
					ZWaveNodeSerializer nodeSerializer = new ZWaveNodeSerializer();
					nodeSerializer.SerializeNode(node);
				}

				// Return here as afterwards we assume there are more elements
				// in the domain array
				return;
			}

			if (splitDomain[2].equals("parameters")) {
				ZWaveConfigurationCommandClass configurationCommandClass = (ZWaveConfigurationCommandClass) node
						.getCommandClass(CommandClass.CONFIGURATION);

				if (configurationCommandClass == null) {
					logger.error("Error getting configurationCommandClass in doAction for node '{}'", nodeId);
					return;
				}
				if (action.equals("Refresh")) {
					logger.debug("Refresh parameters for node '{}'", nodeId);

					ZWaveProductDatabase database = new ZWaveProductDatabase();
					if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) == false) {
						logger.error("Error getting parameters for node '{}' - no database found", nodeId);
						return;
					}

					List<ZWaveDbConfigurationParameter> configList = database.getProductConfigParameters();

					// Request all parameters for this node
					for (ZWaveDbConfigurationParameter parameter : configList)
						this.zController.sendData(configurationCommandClass.getConfigMessage(parameter.Index));
				}
			}

			if (splitDomain[2].equals("wakeup")) {
				if (action.equals("Refresh")) {
					logger.debug("Refresh wakeup capabilities for node '{}'", nodeId);

					ZWaveWakeUpCommandClass wakeupCommandClass = (ZWaveWakeUpCommandClass) node
							.getCommandClass(CommandClass.WAKE_UP);

					if (wakeupCommandClass == null) {
						logger.error("Error getting wakeupCommandClass in doAction for node '{}'", nodeId);
						return;
					}

					// Request the wakeup interval for this node
					this.zController.sendData(wakeupCommandClass.getIntervalMessage());

					// Request the wakeup parameters for this node
					this.zController.sendData(wakeupCommandClass.getIntervalCapabilitiesMessage());
				}
			}
			
			if (splitDomain[2].equals("associations")) {
				if (action.equals("Refresh")) {
					logger.debug("Refresh associations for node '{}'", nodeId);

					ZWaveProductDatabase database = new ZWaveProductDatabase();
					if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) == false) {
						logger.error("Error in doAction for node '{}' - no database found", nodeId);
						return;
					}

					if (splitDomain.length == 3) {
						List<ZWaveDbAssociationGroup> groupList = database.getProductAssociationGroups();

						// Request all parameters for this node
						for (ZWaveDbAssociationGroup group : groupList)
							node.configAssociationReport(group.Index);
					} else if (splitDomain.length == 4) {
						int nodeArg = Integer.parseInt(splitDomain[3].substring(11));
						node.configAssociationReport(nodeArg);
					}
				}
			}
		}
	}

	@Override
	public void doSet(String domain, String value) {
		logger.debug("doSet domain '{}' to '{}'", domain, value);
		String[] splitDomain = domain.split("/");

		// There must be at least 2 components to the domain
		if (splitDomain.length < 2) {
			logger.error("Error malformed domain in doSet '{}'", domain);
			return;
		}

		if (splitDomain[0].equals("nodes")) {
			int nodeId = Integer.parseInt(splitDomain[1].substring(4));

			ZWaveNode node = zController.getNode(nodeId);
			if (node == null) {
				logger.error("Error finding node in doSet '{}'", domain);
				return;
			}

			ZWaveProductDatabase database = new ZWaveProductDatabase();
			if (database.FindProduct(node.getManufacturer(), node.getDeviceType(), node.getDeviceId()) == false) {
				logger.error("Error in doSet for node '{}' - no database found", nodeId);
				return;
			}

			if (splitDomain.length == 3) {
				if (splitDomain[2].equals("Name"))
					node.setName(value);
				if (splitDomain[2].equals("Location"))
					node.setLocation(value);
			} else if (splitDomain.length == 4) {
				if (splitDomain[2].equals("parameters")) {
					ZWaveConfigurationCommandClass configurationCommandClass = (ZWaveConfigurationCommandClass) node
							.getCommandClass(CommandClass.CONFIGURATION);

					if (configurationCommandClass == null) {
						logger.error("Error getting configurationCommandClass in doSet for node '{}'", nodeId);
						return;
					}

					int paramIndex = Integer.parseInt(splitDomain[3].substring(13));
					List<ZWaveDbConfigurationParameter> configList = database.getProductConfigParameters();

					// Get the size
					int size = 1;
					for (ZWaveDbConfigurationParameter parameter : configList) {
						if (parameter.Index == paramIndex) {
							size = parameter.Size;
							break;
						}
					}

					logger.debug("Set parameter index '{}' to '{}'", paramIndex, value);

					ConfigurationParameter configurationParameter = new ConfigurationParameter(paramIndex,
							Integer.valueOf(value), size);
					this.zController.sendData(configurationCommandClass.setConfigMessage(configurationParameter));
				}
				if (splitDomain[2].equals("wakeup")) {
					ZWaveWakeUpCommandClass wakeupCommandClass = (ZWaveWakeUpCommandClass) node
							.getCommandClass(CommandClass.WAKE_UP);

					if (wakeupCommandClass == null) {
						logger.error("Error getting wakeupCommandClass in doSet for node '{}'", nodeId);
						return;
					}
					
					logger.debug("Set wakeup interval to '{}'", value);

					this.zController.sendData(wakeupCommandClass.setInterval(Integer.parseInt(value)));
				}
			} else if (splitDomain.length == 5) {
				if (splitDomain[2].equals("associations")) {
					int assocId = Integer.parseInt(splitDomain[3].substring(11));
					int assocArg = Integer.parseInt(splitDomain[4].substring(4));

					if (value.equalsIgnoreCase("true")) {
						logger.debug("Add association index '{}' to '{}'", assocId, assocArg);
						node.configAssociationAdd(assocId, assocArg);
					} else {
						logger.debug("Remove association index '{}' to '{}'", assocId, assocArg);
						node.configAssociationRemove(assocId, assocArg);
					}
				}
			}
		}
	}

	/**
	 * Event handler method for incoming Z-Wave events.
	 * 
	 * @param event
	 *            the incoming Z-Wave event.
	 */
	@Override
	public void ZWaveIncomingEvent(ZWaveEvent event) {

		// handle association class value events.
		if (event instanceof ZWaveAssociationEvent) {
			handleZWaveAssociationEvent((ZWaveAssociationEvent) event);
			return;
		}

	}

	/**
	 * Handle an incoming configuration parameter events The data is simply
	 * stored into the node for later use.
	 * 
	 * @param event
	 *            the incoming Z-Wave event.
	 */
	private void handleZWaveAssociationEvent(ZWaveAssociationEvent event) {
		logger.debug("Association received nodeId = {}, group = {}, new members = {}", new Object[] {
				event.getNodeId(), event.getGroup(), event.getMemberCnt() });

		// Find the node
		ZWaveNode node = zController.getNode(event.getNodeId());
		if (node == null) {
			logger.error("Configuration parameter for nodeId {}. Node doesn't exist.", event.getNodeId());
			return;
		}

		// Add or update this parameter in the node class
		node.configAssociationAddMembers(event.getGroup(), event.getMembers());
	}

}
