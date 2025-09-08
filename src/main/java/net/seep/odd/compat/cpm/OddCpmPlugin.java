// net/seep/odd/compat/cpm/OddCpmPlugin.java
package net.seep.odd.compat.cpm;

import com.tom.cpm.api.ICPMPlugin;
import com.tom.cpm.api.IClientAPI;
import com.tom.cpm.api.ICommonAPI;

public final class OddCpmPlugin implements ICPMPlugin {
    public static IClientAPI CLIENT; // cached for client calls (nullable)
    public static ICommonAPI COMMON; // not used yet, but handy if you want server-side triggers later

    @Override public void initClient(IClientAPI api) { CLIENT = api; }
    @Override public void initCommon(ICommonAPI api) { COMMON = api; }

    // must be your mod id
    @Override public String getOwnerModId() { return "odd"; }
}
