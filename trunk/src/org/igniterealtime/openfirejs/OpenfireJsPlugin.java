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

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.WrappedException;
import org.ringojs.engine.RhinoEngine;
import org.ringojs.engine.RingoConfiguration;
import org.ringojs.engine.RingoWrapFactory;
import org.ringojs.engine.SyntaxError;
import org.ringojs.repository.FileRepository;
import org.ringojs.repository.Repository;
import org.ringojs.repository.ZipRepository;
import org.ringojs.security.RingoSecurityManager;
import org.ringojs.security.SecureWrapFactory;
import org.ringojs.util.StringUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;

import static java.lang.System.err;
import static java.lang.System.out;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Formatter;
import java.io.IOException;
import java.io.File;
import java.io.PrintStream;


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


    private RingoConfiguration config;
    private RhinoEngine engine;

	private String lastLoadedDate = null;
	private HashMap<String, RhinoEngine> scripts = new HashMap<String, RhinoEngine>();

//-------------------------------------------------------
//
//
//
//-------------------------------------------------------

	public void initializePlugin(PluginManager manager, final File pluginDirectory) {
		Log.info( "["+ NAME + "] Initializing OpenfireJs Plugin");
		Log.info( "["+ NAME + "] OpenfireJs Ringo Version " + RhinoEngine.VERSION.get(0) + "." + RhinoEngine.VERSION.get(1));

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

					List<String> properties = JiveGlobals.getPropertyNames();

					for (String propertyName : properties)
					{
						String propertyValue = JiveGlobals.getProperty(propertyName);

						executeScript(propertyName, propertyValue);
					}
                }

			  	catch (Exception e) {
					Log.error("Error initializing OpenfireJs Plugin ", e);
				}

                return true;
            }
        });

        lastLoadedDate = String.valueOf(new Date());
	}

	private RingoConfiguration createConfig(Repository home)
	{
        try {
			List<String> systemModulePath = new ArrayList<String>();
			List<String> userModulePath = new ArrayList<String>();

			systemModulePath.add("modules");
			systemModulePath.add("packages");

			String extraPath = System.getProperty("ringo.modulepath");

			if (extraPath == null) {
				extraPath = System.getenv("RINGO_MODULE_PATH");
			}

			if (extraPath != null) {
				Collections.addAll(userModulePath, StringUtils.split(extraPath, File.pathSeparator));
			}

			config = new RingoConfiguration(home, userModulePath, systemModulePath);

			boolean hasPolicy = System.getProperty("java.security.policy") != null;
			config.setPolicyEnabled(hasPolicy);
			config.setWrapFactory(hasPolicy ? new SecureWrapFactory() : new RingoWrapFactory());
			config.setArguments(new String[0]);
			config.setOptLevel(JiveGlobals.getIntProperty("rhingo.opt.level", 0));
			config.setBootstrapScripts(new ArrayList<String>());
			config.setDebug(false);
			config.setVerbose(false);
			config.setParentProtoProperties(false);
			config.setStrictVars(true);
			config.setReloading(true);

			return config;

		} catch (Exception e) {
			Log.error("Error OpenfireJs Plugin createConfig ", e);
			return null;
		}
	}


	private void executeScript(String scriptPropertyName, String scriptPropertyValue)
	{
		Repository home = null;

		try {

			if ("js.".equals(scriptPropertyName.substring(0,3))) // script file (.js, .zip, jar)
			{
				String scriptPath = scriptPropertyValue;
				String scriptName = scriptPath + File.separator + scriptPropertyName.substring(3) + ".js";

				File file = new File(scriptPath);
				home = file.isFile() && StringUtils.isZipOrJarFile(scriptPath) ? new ZipRepository(file) : new FileRepository(file);

				config = createConfig(home);

				if (config != null)
				{
					Log.info( "["+ NAME + "] Processing Ringo Script File " + scriptName);

					config.setMainScript(scriptName);

					engine = new RhinoEngine(config, null);
					scripts.put(scriptPropertyName, engine);

					engine.runScript(config.getMainResource(), new String[0]);
					engine.waitTillDone();
				}
			}

			if ("xjs.".equals(scriptPropertyName.substring(0,4))) // executeable script
			{
				String scriptName = scriptPropertyName.substring(4);
				String scriptCode = scriptPropertyValue;

				home = new FileRepository(pluginDirectory);

				config = createConfig(home);

				if (config != null)
				{
					Log.info( "["+ NAME + "] Processing Ringo Script Code " + scriptName);
					Log.debug( "["+ NAME + "] Processing Ringo Script Code " + scriptName + "\n" + scriptCode);

					config.setMainScript(null);

					engine = new RhinoEngine(config, null);
					scripts.put(scriptPropertyName, engine);

					engine.evaluateExpression(scriptCode);
					engine.waitTillDone();

				}
			}

		} catch (Exception e) {

			Log.error("Error running OpenfireJs scripts ", e);
		}
	}


	public void destroyPlugin()
	{
		Log.info( "["+ NAME + "] unloading " + NAME + " plugin resources");

		try {
			SessionEventDispatcher.removeListener(this);
        	UserEventDispatcher.removeListener(this);
			InterceptorManager.getInstance().removeInterceptor(this);

			stopCluster();

			for (RhinoEngine engine : scripts.values())
			{
				Context cx = engine.getContextFactory().enterContext();
				cx.exit();
			}

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
		if (scripts.containsKey(property))
		{
			String value = (String)params.get("value");

			RhinoEngine engine = scripts.get(property);
			Context cx = engine.getContextFactory().enterContext();
			cx.exit();

			executeScript(property, value);
		}
    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {
		if (scripts.containsKey(property))
		{
			RhinoEngine engine = scripts.get(property);
			Context cx = engine.getContextFactory().enterContext();
			cx.exit();
		}
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {

    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {

    }
}