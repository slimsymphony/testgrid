package frank.incubator.testgrid.monitor.utils;


/**
 * LDAP Server model.
 *
 * Presents LDAP server. Includes details for connecting to LDAP.
 *
 * @see LDAPUtil
 * @author larryang
 */

public class LDAPServer{

    private String url;
    private int port;
    private String bindUsername;
    private String bindPassword;

    public LDAPServer (String url, int port, String bindUsername, String bindPassword){
        this.url = url;
        this.port = port;
        this.bindUsername = bindUsername;
        this.bindPassword = bindPassword;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param url the url to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 
     * @return
     */
    public String getBindUsername() {
        return bindUsername;
    }

    /**
     * 
     * @param bindUsername
     */
    public void setBindUsername(String bindUsername) {
        this.bindUsername = bindUsername;
    }

    /**
     * 
     * @return
     */
    public String getBindPassword() {
        return bindPassword;
    }

    /**
     * 
     * @param bindPassword
     */
    public void setBindPassword(String bindPassword) {
        this.bindPassword = bindPassword;
    }
 
}