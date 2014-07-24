package frank.incubator.testgrid.monitor.utils;

/**
 * Data holder for LDAP user information.
 *
 * @author larryang
 */
public class LDAPUser {

    private String username;
    private String realname;
    private String email;
    private String site;

    /**
     *
     */
    public LDAPUser() {
    }

    /**
     *
     * @param username
     * @param email
     * @param realname
     * @param site
     */
    public LDAPUser(String username, String email, String realname, String site) {
        this.username = username;
        this.email = email;
        this.realname = realname;
        this.site = site;
    }

    /**
     *
     * @return
     */
    public String getEmail() {
        return email;
    }

    /**
     *
     * @param email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     *
     * @return
     */
    public String getUsername() {
        return username;
    }

    /**
     *
     * @param username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     *
     * @return
     */
    public String getRealname() {
        return realname;
    }

    /**
     *
     * @param realname
     */
    public void setRealname(String realname) {
        this.realname = realname;
    }

    /**
     *
     * @return
     */
    public String getSite() {
        return site;
    }

    /**
     *
     * @param site
     */
    public void setSite(String site) {
        this.site = site;
    }
}
