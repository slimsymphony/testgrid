/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.dm;

/**
 *
 * @author larryang
 */
public class SysUser {
    
    private String userName;
    private UserRole userRole;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void setUserRole(UserRole userRole) {
        this.userRole = userRole;
    }
    
}
