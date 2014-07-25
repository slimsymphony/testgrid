package frank.incubator.testgrid.monitor.utils;

import com.unboundid.ldap.sdk.LDAPException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractLDAPUtil {

    /**
     *
     * @param userId
     * @return
     */
    public static boolean isNextUser(String userId) {
        Pattern pattern = Pattern.compile("^e[0-9]{3,}", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(userId);
        return matcher.find();
    }

    /**
     *
     * @param username
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException
     */
    public abstract LDAPUser getByUsername(String username) throws LDAPException, GeneralSecurityException;

    /**
     *
     * @param query
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException
     */
    public abstract List<LDAPUser> search(String query) throws LDAPException, GeneralSecurityException;

    /**
     *
     * @param username
     * @param password
     * @return
     * @throws LDAPException
     * @throws GeneralSecurityException
     */
    public abstract LDAPUser authenticate(String username, String password) throws LDAPException, GeneralSecurityException;

    /**
     *
     */
    public AbstractLDAPUtil() {
        super();
    }
    
    
}
