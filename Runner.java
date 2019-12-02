import com.jcraft.jsch.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;

public class Runner {
    private static final Log LOGGER = LogFactory.getLog(Runner.class);
    private static SSH ssh;

    public static void start() {
        LOGGER.info("Starting");
        ssh = new SSH();
        String host= JOptionPane.showInputDialog("Enter username@hostname",
                System.getProperty("user.name")+
                        "@localhost");
        String user=host.substring(0, host.indexOf('@'));
        host=host.substring(host.indexOf('@')+1);

        String password = JOptionPane.showInputDialog("Enter password");

        ssh.connect(host,user, password);
    }

    public static class SSH {
        private final Log LOGGER = LogFactory.getLog(Runner.class);
        private JSch jsch;
        private int port;

        public SSH() {
            this.port = 22;
            jsch = new JSch();
        }

        public void connect(String host, String username, String password) {
            LOGGER.error("Connecting");
            try {
                Session session = jsch.getSession(username, host, port);
                session.setPassword(password);
                UserInfo ui = new MyUserInfo(){
                    public void showMessage(String message){
                        JOptionPane.showMessageDialog(null, message);
                    }
                    public boolean promptYesNo(String message){
                        Object[] options={ "yes", "no" };
                        int foo=JOptionPane.showOptionDialog(null,
                                message,
                                "Warning",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.WARNING_MESSAGE,
                                null, options, options[0]);
                        return foo==0;
                    }
                };
                session.setUserInfo(ui);
                session.connect(30000);//making a connection with timeout

                Channel channel = session.openChannel("shell");
                channel.setInputStream(System.in);
                channel.setOutputStream(System.out);

                channel.connect(3000);



            }catch (Exception e){
                LOGGER.error("Error with connecting to the host");
                LOGGER.error(e.toString());
                e.printStackTrace();
            }
    }

    public static void main(String[] arg){
        start();
    }
        public static abstract class MyUserInfo
                implements UserInfo, UIKeyboardInteractive {
            public String getPassword(){ return null; }
            public boolean promptYesNo(String str){ return false; }
            public String getPassphrase(){ return null; }
            public boolean promptPassphrase(String message){ return false; }
            public boolean promptPassword(String message){ return false; }
            public void showMessage(String message){ }
            public String[] promptKeyboardInteractive(String destination,
                                                      String name,
                                                      String instruction,
                                                      String[] prompt,
                                                      boolean[] echo){
                return null;
            }
        }
}}
