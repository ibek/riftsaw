/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.switchyard.component.bpel.riftsaw;

import javax.xml.namespace.QName;

import org.apache.log4j.Logger;
import org.hibernate.util.XMLHelper;
import org.milyn.xml.XmlUtil;
import org.riftsaw.engine.BPELEngine;
import org.riftsaw.engine.DeploymentUnit;
import org.riftsaw.engine.Fault;
import org.switchyard.Exchange;
import org.switchyard.ExchangePattern;
import org.switchyard.HandlerException;
import org.switchyard.Message;
import org.switchyard.ServiceDomain;
import org.switchyard.ServiceReference;
import org.switchyard.component.bpel.config.model.BPELComponentImplementationModel;
import org.switchyard.component.bpel.exchange.BaseBPELExchangeHandler;
import org.switchyard.exception.SwitchYardException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A Riftsaw implementation of a BPEL ExchangeHandler.
 *
 */
public class RiftsawBPELExchangeHandler extends BaseBPELExchangeHandler {

    private static final Logger logger = Logger.getLogger(RiftsawBPELExchangeHandler.class);

    private final ServiceDomain m_serviceDomain;
    private BPELEngine m_engine=null;
    private QName m_serviceName=null;
    private javax.wsdl.Definition m_wsdl=null;
    private javax.wsdl.PortType m_portType=null;
    private String m_portName=null;
    private String m_version=null;

    /**
     * Constructs a new RiftSaw BPEL ExchangeHandler within the specified ServiceDomain.
     * 
     * @param serviceDomain the specified ServiceDomain
     */
    public RiftsawBPELExchangeHandler(ServiceDomain serviceDomain) {
        m_serviceDomain = serviceDomain;
    }

    /**
     * {@inheritDoc}
     */
    public void init(QName qname, BPELComponentImplementationModel model,
    				javax.wsdl.Definition wsdl, BPELEngine engine) {
    	
    	m_engine = engine;
    	m_serviceName = QName.valueOf(model.getServiceName());
    	m_portName = model.getPortName();
    	m_version = model.getVersion();
    	m_wsdl = wsdl;
    	
    	// Obtain the port type from the service and port name
    	javax.wsdl.Service service=m_wsdl.getService(m_serviceName);
    	javax.wsdl.Port port=service.getPort(m_portName);
    	
    	m_portType = port.getBinding().getPortType();
    	
    	String descriptor=model.getProcessDescriptor();
    	
		java.net.URL url=ClassLoader.getSystemResource(descriptor);
		
		java.io.File deployFile=new java.io.File(url.getFile());
		
		DeploymentUnit bdu=new DeploymentUnit(qname.getLocalPart(), m_version,
							deployFile.lastModified(), deployFile);

		// Deploy the process
		engine.deploy(bdu);
    }

    /**
     * {@inheritDoc}
     */
    public void start(ServiceReference serviceRef) {
    	System.out.println("START: "+serviceRef);
    }

    /**
     * {@inheritDoc}
     */
    public void handleMessage(Exchange exchange) throws HandlerException {
        
    	if (exchange.getContract().getServiceOperation().getExchangePattern().equals(ExchangePattern.IN_OUT)) {
            Node request = exchange.getMessage().getContent(Node.class);
            
            java.util.Map<String,Object> headers=new java.util.HashMap<String, Object>();
            
            try {
            	// Find part name associated with operation on port type
            	String partName=getPartName(exchange.getContract().getServiceOperation().getName());
            	
            	// Create wrapper for request message, adding it to the appropriate named
            	// part associated with the operation being involed
            	Element newreq=request.getOwnerDocument().createElement("message");
            	Element part=request.getOwnerDocument().createElement(partName);
            	newreq.appendChild(part);
            	part.appendChild(request);
            	
            	// Invoke the operation on the BPEL process
            	Element response=m_engine.invoke(m_serviceName, m_portName,
            			exchange.getContract().getServiceOperation().getName(),
            					newreq, headers);

                Message message = exchange.createMessage();
                
                // Strip off wrapper and part to just return the part contents
                message.setContent(response.getFirstChild().getFirstChild());
                
                exchange.send(message);

            } catch(Fault f) {
            	Message faultMessage=exchange.createMessage();
            	faultMessage.setContent(f.getFaultMessage());
            	
            	// TODO: What about the fault code? - do we need to strip off part wrapper?
            	
            	exchange.sendFault(faultMessage);
            	
            } catch(Exception e) {
            	e.printStackTrace();
            }
        }
    }
    
    protected String getPartName(String operationName) {
    	String ret=null;
    	javax.wsdl.Operation op=m_portType.getOperation(operationName, null, null);
    	
    	if (op != null) {
    		java.util.Map<?,?> parts=op.getInput().getMessage().getParts();
    		
    		if (parts.size() != 1) {
    			throw new SwitchYardException("Only expecting a single message part for operation '"+
    						operationName+", on service "+m_serviceName+" port "+m_portName);
    		}
    		
    		ret = (String)parts.keySet().iterator().next();
    	}
    	
    	return(ret);
    }

     /**
     * {@inheritDoc}
     */
    public void stop(ServiceReference serviceRef) {
    	System.out.println("STOP: "+serviceRef);
    }

    /**
     * {@inheritDoc}
     */
    public void destroy(ServiceReference serviceRef) {
    	System.out.println("DESTROY: "+serviceRef);
    }

}
