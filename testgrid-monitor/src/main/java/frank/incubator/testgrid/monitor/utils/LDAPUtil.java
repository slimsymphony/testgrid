package frank.incubator.testgrid.monitor.utils;

/**
 *
 * @author larryang
 */
import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.DereferencePolicy;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;


/**
 *
 * @author larryang
 */
public class LDAPUtil extends AbstractLDAPUtil {

    private final String DEFAULT_URL = "147.243.4.80";
    private final int DEFAULT_PORT = 636;
    private BindRequest bindRequest;
    private String url;
    private int port;
   
    /**
     * @param ldapServer
     */
    public LDAPUtil(LDAPServer ldapServer) {
        super();
        if (ldapServer != null) {
            url = ldapServer.getUrl();
            port = ldapServer.getPort();
            if (StringUtils.isNotEmpty(ldapServer.getBindUsername())
                    && StringUtils.isNotEmpty(ldapServer.getBindPassword())) {
                bindRequest = new SimpleBindRequest(ldapServer.getBindUsername(), ldapServer.getBindPassword());
            }
        } else {
            url = DEFAULT_URL;
            port = DEFAULT_PORT;
        }
    }

    /**
     *
     * @param username
     * @param password
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException
     */
    @Override
    public LDAPUser authenticate(String username, String password) throws LDAPException, GeneralSecurityException {

        LDAPConnection ldap = null;
        try {
            SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
            ldap = new LDAPConnection(sslUtil.createSSLSocketFactory());
            ldap.connect(url, port);
            if (bindRequest != null) {
                ldap.bind(bindRequest);
            }
            String[] searchParams = {"mail", "cn", "site"};
            SearchResult sr = ldap.search("o=Ncic", SearchScope.SUB, "(uid=" + username + ")", searchParams);
            if (sr.getEntryCount() == 0) {
                return null;
            }
            SearchResultEntry entry = sr.getSearchEntries().get(0);
            String dn = entry.getDN();
            String email = entry.getAttributeValue("mail");
            String realname = entry.getAttributeValue("cn");
            String site = entry.getAttributeValue("site");

            ldap.bind(dn, password);

            return new LDAPUser(username, email, realname, site);
        } finally {
            if (ldap != null) {
                ldap.close();
            }
        }
    }

    /**
     *
     * @param query
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException
     */
    @Override
    public List<LDAPUser> search(String query) throws LDAPException, GeneralSecurityException {
        List<LDAPUser> results = new ArrayList<LDAPUser>();

        LDAPConnection ldap = null;
        try {
            SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
            ldap = new LDAPConnection(sslUtil.createSSLSocketFactory());
            ldap.connect(url, port);
            if (bindRequest != null) {
                ldap.bind(bindRequest);
            }
            String[] searchParams = {"mail", "cn", "site", "uid"};
            String searchQuery = "(|(uid=" + query + "*)(cn=" + query + "*)(mail=" + query + "*))";
            SearchResult sr;
            try {
                sr = ldap.search("o=Nokia", SearchScope.SUB, DereferencePolicy.SEARCHING, 10, 60, false,
                        searchQuery, searchParams);
            } catch (LDAPSearchException e) {
                // Only if the size limit has been exceeded
                sr = e.getSearchResult();
            }

            if (sr != null) {
                for (SearchResultEntry e : sr.getSearchEntries()) {
                    String dn = e.getAttributeValue("uid");
                    String email = e.getAttributeValue("mail");
                    String realname = e.getAttributeValue("cn");
                    String site = e.getAttributeValue("site");
                    LDAPUser user = new LDAPUser(dn, email, realname, site);
                    results.add(user);
                }
            }
        } finally {
            if (ldap != null) {
                ldap.close();
            }
        }
        return results;
    }

    /**
     *
     * @param username
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException
     */
    @Override
    public LDAPUser getByUsername(String username) throws LDAPException, GeneralSecurityException {
        LDAPConnection ldap = null;
        try {
            SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
            ldap = new LDAPConnection(sslUtil.createSSLSocketFactory());
            ldap.connect(url, port);
            if (bindRequest != null) {
                ldap.bind(bindRequest);
            }
            String[] searchParams = {"mail", "cn", "site", "uid"};
            String searchQuery = "(uid=" + username + ")";
            SearchResult sr = ldap.search("o=Ncic", SearchScope.SUB, DereferencePolicy.SEARCHING, 1, 60, false,
                    searchQuery, searchParams);
            if (sr.getEntryCount() == 0) {
                return null;
            }
            SearchResultEntry entry = sr.getSearchEntries().get(0);
            String email = entry.getAttributeValue("mail");
            String realname = entry.getAttributeValue("cn");
            String site = entry.getAttributeValue("site");

            return new LDAPUser(username, email, realname, site);
        } finally {
            if (ldap != null) {
                ldap.close();
            }
        }
    }

    /**
     *
     * @param email
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException 
     */
    public String getUserIdByEmail(String email) throws LDAPException, GeneralSecurityException {

        LDAPConnection ldap = null;
        try {
            SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
            ldap = new LDAPConnection(sslUtil.createSSLSocketFactory());
            ldap.connect(url, port);
            if (bindRequest != null) {
                ldap.bind(bindRequest);
            }
            String[] searchParams = {"uid"};
            String searchQuery = "(mail=" + email + ")";
            SearchResult sr = ldap.search("o=Ncic", SearchScope.SUB, DereferencePolicy.SEARCHING, 1, 60, false,
                    searchQuery, searchParams);
            if (sr.getEntryCount() == 0) {
                return null;
            }
            SearchResultEntry entry = sr.getSearchEntries().get(0);
            return entry.getAttributeValue("uid");

        } finally {
            if (ldap != null) {
                ldap.close();
            }
        }
    }
}
