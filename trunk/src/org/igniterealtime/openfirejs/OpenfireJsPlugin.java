package org.igniterealtime.openfirejs;

import java.io.File;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;


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


public class OpenfireJsPlugin implements Plugin, PropertyEventListener, OpenfireJsConstants {

	private static final String NAME 		= "openfirejs";
	private static final String DESCRIPTION = "OpenfireJs Plugin";
	private static final Logger Log = LoggerFactory.getLogger(OpenfireJsPlugin.class);

	private PluginManager manager;
    private File pluginDirectory;
    private ExecutorService executor;
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

        PropertyEventDispatcher.addListener(this);

		executor = Executors.newCachedThreadPool();

        executor.submit(new Callable<Boolean>()
        {
            public Boolean call() throws Exception {
                try {
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

				File file = new File(scriptPath);
				home = file.isFile() && StringUtils.isZipOrJarFile(scriptPath) ? new ZipRepository(file) : new FileRepository(file);

				config = createConfig(home);

				if (config != null)
				{
					Log.info( "["+ NAME + "] Processing Ringo Script File " + scriptPath);

					config.setMainScript(scriptPath);

					engine = new RhinoEngine(config, null);
					scripts.put(scriptPropertyName, engine);

					engine.runScript(config.getMainResource(), new String[0]);
					engine.waitTillDone();
				}
			}

			if ("xjs.".equals(scriptPropertyName.substring(0,4))) // executeable script
			{
				String scriptCode = scriptPropertyValue;

				home = new FileRepository(pluginDirectory);

				config = createConfig(home);

				if (config != null)
				{
					Log.info( "["+ NAME + "] Processing Ringo Script Code " + scriptPropertyName);
					Log.debug( "["+ NAME + "] Processing Ringo Script Code " + scriptPropertyName + "\n" + scriptCode);

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


//-------------------------------------------------------
//
//
//
//-------------------------------------------------------


    public void propertySet(String property, Map params)
    {
		if ("js.".equals(property.substring(0,3)) || "xjs.".equals(property.substring(0,4)))
		{
			String value = (String)params.get("value");

			if (scripts.containsKey(property))
			{
				RhinoEngine engine = scripts.get(property);
				Context cx = engine.getContextFactory().enterContext();
				cx.exit();
			}

			executeScript(property, value);
		}
    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {
		if (scripts.containsKey(property))
		{
			RhinoEngine engine = scripts.remove(property);
			Context cx = engine.getContextFactory().enterContext();
			cx.exit();
		}
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {

    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {

    }
}