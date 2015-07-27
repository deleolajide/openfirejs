=OpenfireJs=

OpenfireJs is a CommonJS-based JavaScript runtime engine based on [http://http://ringojs.org/ RhinoJs] which exposes the API of an Openfire server and its plugins to server-side Javascript

In other words, you will be able to extend Openfire with scripts that can directly access features of the server and any installed plugin. In effect you can write an Openfire plugin in JavaScript.

In practice, you should be able to do the following:

 * Register a script as a Component. Components receive all packets addressed to a particular sub-domain. For example, test_component.example.com. So, a packet sent to joe@test_component.example.com would be delivered to the component. Note that the sub-domains defined as components are unrelated to DNS entries for sub-domains. All XMPP routing at the socket level is done using the primary server domain (example.com in the example above); sub-domains are only used for routing within the XMPP server.

 * Register a plugin as an IQHandler. IQ handlers respond to IQ packets with a particular element name and namespace. 
  
 * Register a plugin as a PacketInterceptor to receive all packets being sent through the system and optionally reject them. For example, an interceptor could reject all messages that contained profanity or flag them for review by an administrator.

 * You can store persistent plugin settings as Openfire properties. 

 * Perform bulk data manipulation with script object like PowerScripts. For example, admin/support tasks like creation/migration of Openfire user/groups.

----



==To run a Ringo script file or package (.js, .zip, .jar)==

Simple two-line script saved in file hello.world.js

{{{
var log = Packages.org.jivesoftware.util.Log; 
log.info("Hello World from a Script file edited !!");
}}}

 * Create a system property that starts with "js". For example js.hello.world. 
 * Set the value to the full file location

http://openfirejs.googlecode.com/files/Image12.jpg

The plugin will detect the creation of the property and immediately run the script. If you make changes later, the script will be stopped and the new file will be started.

http://openfirejs.googlecode.com/files/Image10.jpg

==To run a simple script from the Openfire admin web console==

 * Create a property that starts with "xjs". For example xjs.hello.world. 
 * Type the script code as the value.

http://openfirejs.googlecode.com/files/Image6.jpg

The plugin will detect the creation of the property and immediately run the script code. If you make changes later, the script will be stopped and the new code will be run.

=More examples=

1. *XML Debugger. Intercept every messages using the Openfire packet interceptor and write to log file*

{{{

var log = Packages.org.jivesoftware.util.Log; 
var interceptorMgr = Packages.org.jivesoftware.openfire.interceptor.InterceptorManager; 
var myInterceptor =  { interceptPacket: function (packet, session, incoming, processed) 
{
	if (processed) log.info(packet); 
} }

var interceptor = new org.jivesoftware.openfire.interceptor.PacketInterceptor(myInterceptor);

interceptorMgr.getInstance().addInterceptor(interceptor);

}}}

*2. Create a bot user using an xmpp componnent*

{{{

var componentFactory = Packages.org.xmpp.component.ComponentManagerFactory;

var myComponent =  {
	getDescription: function () {return "hello component" },
	getDomain: function () {return "btg199251" },	
	getName: function () {return "hello" },	
	
	handleMessage: function (received) 
	{
		var response = new Packages.org.xmpp.packet.Message();
		
		response.setFrom("bot@hello.btg199251");
		response.setTo(received.getFrom());
		response.setBody("Hello!");

		this.send(response);
	}		
}

var component = new JavaAdapter(Packages.org.xmpp.component.AbstractComponent, myComponent);
componentFactory.getComponentManager().addComponent("hello", component);

}}}
