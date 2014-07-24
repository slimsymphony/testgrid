package frank.incubator.testgrid.common.model;

import static frank.incubator.testgrid.common.CommonUtils.toJson;

import java.util.Observable;

//import com.google.common.hash.Hasher;
//import com.google.common.hash.Hashing;
/**
 * Base Object for all Model Class Object. Provide some convenient method
 * inherit for subclasses.
 * 
 * @author Wang Frank
 * 
 */
public abstract class BaseObject extends Observable {

	protected String id;
    protected long lastUpdated = System.currentTimeMillis();

    //private static Hasher hc = Hashing.md5().newHasher();
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj.getClass().equals(this.getClass()))) {
            return false;
        }
        if (this.id.equals(((BaseObject) obj).getId())) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return id.hashCode();
        //return hc.putUnencodedChars( id ).hash().asInt();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return toJson(this);
    }

    public String getClassType() {
        return this.getClass().getCanonicalName();
    }

    public void markChange() {
        this.setChanged();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void notifyObservers(Object arg) {
        this.lastUpdated = System.currentTimeMillis();
        super.notifyObservers(arg);
    }

    public long calcTimeNotUpdatedTillNow(){
        return ((System.currentTimeMillis() - lastUpdated) / 1000l / 60l );
    }

}
