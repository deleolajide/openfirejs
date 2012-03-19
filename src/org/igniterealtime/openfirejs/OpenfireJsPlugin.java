package org.igniterealtime.openfirejs;

import java.io.File;

import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.PrivateStorage;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;

import org.jivesoftware.util.Log;

import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;

import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;



public class OpenfireJsPlugin implements Plugin, PropertyEventListener, ClusterEventListener,  UserEventListener, SessionEventListener, PacketInterceptor, OpenfireJsConstants {

	private static final String NAME 		= "openfirejs";
	private static final String DESCRIPTION = "OpenfireJs Plugin";

	private PluginManager manager;
    private File pluginDirectory;
    private ComponentManager componentManager;
    private UserManager userManager;
    private PresenceManager presenceManager;

	private WebAppContext context;
	private ContextHandlerCollection contexts;
    private ExecutorService executor;
	private PrivateStorage privateStorage;

	public String lastLoadedDate = null;

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------

	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		Log.info( "["+ NAME + "] Initializing OpenfireJs Plugin");

		this.manager = manager;
		this.pluginDirectory = pluginDirectory;

		componentManager 	= ComponentManagerFactory.getComponentManager();
        userManager 		= UserManager.getInstance();
		presenceManager 	= XMPPServer.getInstance().getPresenceManager();
        privateStorage 		= XMPPServer.getInstance().getPrivateStorage();

		SessionEventDispatcher.addListener(this);
		UserEventDispatcher.addListener(this);
        PropertyEventDispatcher.addListener(this);

		InterceptorManager.getInstance().addInterceptor(this);

		executor = Executors.newCachedThreadPool();

        executor.submit(new Callable<Boolean>()
        {
            public Boolean call() throws Exception {
                try {
					startCluster();
					createWebAppContext();
                }
                catch (Exception e) {
                    Log.error("Error initializing OpenfireJs Plugin", e);
                }

                return true;
            }
        });

        lastLoadedDate = String.valueOf(new Date());
	}

	public void destroyPlugin() {
		Log.info( "["+ NAME + "] unloading " + NAME + " plugin resources");

		try {
			SessionEventDispatcher.removeListener(this);
        	UserEventDispatcher.removeListener(this);
			InterceptorManager.getInstance().removeInterceptor(this);

			stopCluster();
			executor.shutdown();
		}
		catch (Exception e) {
			Log.error("destroyPlugin " + e);
		}
	}

	public String getName() {
		 return NAME;
	}

	public String getDescription() {
		return DESCRIPTION;
	}

	public ComponentManager getComponentManager() {
        return componentManager;
    }


	public void createWebAppContext() {

		try {
			Log.info( "["+ NAME + "] createWebAppContext - Creating web service " + NAME);

			contexts = HttpBindManager.getInstance().getContexts();

			try {
				context = new WebAppContext(contexts, pluginDirectory.getPath(), "/" + NAME);
				context.setWelcomeFiles(new String[]{"index.html"});
			}
			catch(Exception e) {

        	}
        }
        catch(Exception e) {
			Log.error("["+ NAME + "] createWebAppContext exception " + e);
        }
	}

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------

    public void joinedCluster()
    {
		Log.info( "["+ NAME + "] joinedCluster");

    }

    public void joinedCluster(byte[] nodeID)
    {

    }

    public void leftCluster()
    {
		Log.info( "["+ NAME + "] leftCluster");

    }

    public void leftCluster(byte[] nodeID)
    {

    }

    public void markedAsSeniorClusterMember()
    {
		Log.info( "["+ NAME + "] markedAsSeniorClusterMember");
    }

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


    public void startCluster()
    {
		Log.info( "["+ NAME + "] startCluster - Adding listener");
		ClusterManager.addListener(this);
	}

    private void stopCluster()
    {
		Log.info( "["+ NAME + "] stopCluster - Removing listener");
        ClusterManager.removeListener(this);
	}

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


	public void anonymousSessionCreated(Session session)
	{

	}

	public void anonymousSessionDestroyed(Session session)
	{

	}

	public void resourceBound(Session session)
	{

	}

	public void sessionCreated(Session session)
	{

	}

	public void sessionDestroyed(Session session)
	{

	}

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException
    {
        if (!processed && packet instanceof IQ)
        {

        }

    }

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------

    public void userCreated(User user, Map params)
    {

    }

    public void userDeleting(User user, Map params)
    {

    }

    public void userModified(User user, Map params)
    {

    }

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


	public String getDomain()
	{
		String hostName =  XMPPServer.getInstance().getServerInfo().getHostname();
		return JiveGlobals.getProperty("xmpp.domain", hostName);
	}

    public void propertySet(String property, Map params)
    {

    }

    public void propertyDeleted(String property, Map params)
    {

    }

    public void xmlPropertySet(String property, Map params) {

    }

    public void xmlPropertyDeleted(String property, Map params) {

    }
}