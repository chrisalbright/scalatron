package scalatron.webServer

import org.eclipse.jetty
import java.net.{UnknownHostException, InetAddress}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import javax.servlet.http.HttpServlet
import rest.RestApplication
import scalatron.scalatron.api.Scalatron
import com.sun.jersey.spi.container.servlet.ServletContainer
import servelets.{AdminServlet, UserServlet, HomePageServlet, WebContext}
import akka.actor.ActorSystem


/** Entry point for the web server that serves the Scalatron RESTful API and the browser UI ("Scalatron IDE").
  * Currently this uses Jetty, but it would be nice to migrate this to somthing based on Akka (e.g., Spray?)
  * at some point. That's why apply() receives a reference to the Akka actor system, which is not currently used.
  */
object WebServer {
    def printArgList() {
        println("  -webui <dir>             directory containing browser UI (default: ../webui)")
        println("  -users <dir>             directory containing browser UI workspaces (default: ../users)")
        println("  -port <int>              port to serve browser UI at (default: 8080)")
        println("  -browser yes|no          open a browser showing Scalatron IDE (default: yes)")
    }

    def apply(actorSystem: ActorSystem, scalatron: Scalatron, argMap: Map[String, String], verbose: Boolean) = {

        val webServerPort = argMap.get("-port").map(_.toInt).getOrElse(8080)
        if (verbose) println("Browser UI will be served on port: " + webServerPort)

        // for a help message, find out the hostname and IP address
        val hostname =
            try {
                val addr = InetAddress.getLocalHost

                try {
                    // Get hostname
                    addr.getHostName
                } catch {
                    case e: UnknownHostException =>
                        // val ipAddr = addr.getAddress // Get IP Address
                        "localhost" // better: render the IP
                }
            } catch {
                case t: Throwable => "localhost" // Oh well
            }

        val browserUiUrl = "http://" + hostname + ":" + webServerPort + "/"
        println("Players should point their browsers to: " + browserUiUrl)



        // extract the web UI base directory from the command line ("/webui")
        // construct the complete plug-in path and inform the user about it
        val webUiBaseDirectoryPathFallback = scalatron.installationDirectoryPath + "/" + "webui"
        val webUiBaseDirectoryPathArg = argMap.get("-webui").getOrElse(webUiBaseDirectoryPathFallback)
        val webUiBaseDirectoryPath = if (webUiBaseDirectoryPathArg.last == '/') webUiBaseDirectoryPathArg.dropRight(1) else webUiBaseDirectoryPathArg
        if (verbose) println("Will search for web UI files in: " + webUiBaseDirectoryPath)

        // extract the web user base directory from the command line ("/webuser")
        val webUserBaseDirectoryPathFallback = scalatron.installationDirectoryPath + "/" + "users"
        val webUserBaseDirectoryPathArg = argMap.get("-users").getOrElse(webUserBaseDirectoryPathFallback)
        val webUserBaseDirectoryPath = if (webUserBaseDirectoryPathArg.last == '/') webUserBaseDirectoryPathArg.dropRight(1) else webUserBaseDirectoryPathArg
        if (verbose) println("Will maintain web user content in: " + webUserBaseDirectoryPath)




        val webCtx = WebContext(scalatron, webUiBaseDirectoryPath, verbose)

        val jettyServer = new jetty.server.Server(webServerPort)

        val context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/")
        context.addServlet(holder(HomePageServlet(webCtx)), "/*")
        context.addServlet(holder(UserServlet(webCtx)), "/user/*")
        context.addServlet(holder(AdminServlet(webCtx)), "/admin/*")

        val jerseyServlet: ServletContainer = new ServletContainer(new RestApplication(scalatron, verbose))

        context.addServlet(holder(jerseyServlet), "/api/*");

        jettyServer.setHandler(context)

        val webServer = new WebServer(jettyServer, verbose)


        // optionally: open a browser showing the browser UI
        val openBrowser = argMap.get("-browser").getOrElse("yes") != "no"
        if(openBrowser && java.awt.Desktop.isDesktopSupported) {
            val desktop = java.awt.Desktop.getDesktop
            if( desktop.isSupported( java.awt.Desktop.Action.BROWSE ) ) {
                new Thread(new Runnable { def run() {
                    try {
                        val waitTimeBeforeLaunchingBrowser = 3000 // give web server some time to start up
                        Thread.sleep(waitTimeBeforeLaunchingBrowser)
                        desktop.browse(new java.net.URI(browserUiUrl))
                    } catch {
                        case t: Throwable => if(verbose) System.err.println("warning: failed to open browser (for convenience only)")
                    }
                } }).start()
            }
        }


        webServer
    }

    def holder(s: HttpServlet): ServletHolder = new ServletHolder(s);

}


class WebServer(server: jetty.server.Server, verbose: Boolean) {
    def start() {
        if (verbose) println("Starting browser front-end...")
        try {
            server.start()
        } catch {
            case t: Throwable => System.err.println("error: failed to start browser front-end: " + t)
        }
    }

    def stop() {
        if (verbose) println("Stopping browser front-end...")
        server.stop()
    }

}


