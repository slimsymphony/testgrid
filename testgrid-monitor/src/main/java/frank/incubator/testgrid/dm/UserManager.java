/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.dm;

import java.util.List;
import java.util.Map;

/**
 *
 * @author larryang
 */
public interface UserManager {
    
    void addUser(SysUser user) throws UserManageException;
    void updateUser(SysUser user) throws UserManageException;
    List<SysUser> queryUsers(Map<String,? extends Object> conditions);
    
}
