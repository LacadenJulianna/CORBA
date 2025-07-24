import GameApp.*;
import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import org.omg.CosNaming.*;

public class GameServerApp {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            GameServer server = new GameServer();
            server.setORB(orb);

            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(server);
            GameService href = GameServiceHelper.narrow(ref);

            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            NameComponent path[] = ncRef.to_name("Game");
            ncRef.rebind(path, href);

            System.out.println("Game Server ready...");
            orb.run();
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace();
        }
        System.out.println("Server Exiting...");
    }
}
