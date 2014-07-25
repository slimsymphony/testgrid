/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.dm;

import static frank.incubator.testgrid.common.CommonUtils.closeQuietly;

import frank.incubator.testgrid.common.CommonUtils;
import frank.incubator.testgrid.common.log.LogConnector;
import frank.incubator.testgrid.common.log.LogUtils;
import frank.incubator.testgrid.monitor.ServiceAdapter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.midi.MidiDevice.Info;
import org.apache.commons.lang3.StringUtils;

public class UserManagerImpl implements UserManager {

    private LogConnector log;

    public UserManagerImpl() {
        log = LogUtils.get("sqlite");
    }

    @Override
    public void addUser(final SysUser user) throws UserManageException {
        if (user == null || user.getUserName() == null) {
            throw new UserManageException("Invalid User provided for Adding.user=" + user);
        }
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            @SuppressWarnings("serial")
            List<SysUser> candidates = queryUsers(new HashMap<String, String>() {

                {
                    this.put("username", user.getUserName());
                }
            });
            if (candidates != null && !candidates.isEmpty()) {
                //updateUser(user);
                log.debug("User[username:" + user.getUserName() + "] have already been exist, just keep it and no updat.");
            } else {
                conn = ServiceAdapter.getDbHelper().getConnection();
                ps = conn.prepareStatement("insert into users( userrole, username ) values(?,?)");
                inject(user, ps);
                ps.executeUpdate();
                log.debug("User[username:" + user.getUserName() + "] been added.");
            }
        } catch (Exception ex) {
            log.error("Add User got exception.", ex);
            throw new UserManageException(ex);
        } finally {
            closeQuietly(ps);
            closeQuietly(conn);
        }
    }

    private void inject(SysUser user, PreparedStatement ps) throws SQLException {
        ps.setString(1, (user.getUserRole() == null ? "" : user.getUserRole().toString()));
        ps.setString(2, user.getUserName());
    }

    @Override
    public void updateUser(SysUser user) throws UserManageException {
        if (user == null || user.getUserName() == null) {
            throw new UserManageException("Invalid user provided for Updating.user=" + user);
        }
        Connection conn = null;
        PreparedStatement ps = null;
        String sql = "update users set userrole=? where username=?";
        try {
            conn = ServiceAdapter.getDbHelper().getConnection();
            ps = conn.prepareStatement(sql);
            inject(user, ps);
            ps.executeUpdate();
            log.info("Update user[" + user + "] success.");
        } catch (Exception ex) {
            throw new UserManageException("Update user:[" + user + "] failed.", ex);
        } finally {
            closeQuietly(ps);
            closeQuietly(conn);
        }
    }

    @Override
    public List<SysUser> queryUsers(Map<String, ? extends Object> conditions) {

        if (conditions == null) {
            conditions = new HashMap<String, Object>();
        }
        List<SysUser> sysUsers = new ArrayList<SysUser>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        StringBuffer sql = new StringBuffer("select * from users where ");
        try {
            conn = ServiceAdapter.getDbHelper().getConnection();
            createCondition(sql, conditions);
            sql.append("1=1");
            log.info("Query user: " + sql.toString());
            ps = conn.prepareStatement(sql.toString());
            setCondition(ps, conditions);
            rs = ps.executeQuery();
            while (rs.next()) {
                sysUsers.add(extract(rs));
            }
            log.info("Found users: " + sysUsers.size() + sysUsers.toString());
        } catch (Exception ex) {
            log.error("Query Users got exception, conditions=[" + CommonUtils.toJson(conditions) + "]", ex);
        } finally {
            closeQuietly(rs);
            closeQuietly(ps);
            closeQuietly(conn);
        }
        return sysUsers;

    }

    private SysUser extract(ResultSet rs) throws SQLException {
        SysUser sysUser = new SysUser();
        sysUser.setUserName(rs.getString("username"));
        sysUser.setUserRole(StringUtils.isEmpty(rs.getString("userrole")) ? null : UserRole.valueOf(rs.getString("userrole")));
        return sysUser;
    }

    private void createCondition(StringBuffer sql, Map<String, ? extends Object> conditions) {
        for (String key : conditions.keySet()) {
            sql.append(key).append("=?").append(" and ");
        }
    }

    private void setCondition(PreparedStatement ps, Map<String, ? extends Object> conditions) throws SQLException {
        int counter = 1;
        Object val = null;
        for (String key : conditions.keySet()) {
            val = conditions.get(key);
            if (val == null || val instanceof String) {
                ps.setString(counter++, (String) val);
            } else if (val instanceof Integer) {
                ps.setInt(counter++, (int) val);
            }
        }
    }
}
