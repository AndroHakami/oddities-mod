package net.seep.odd.abilities.zerosuit;

import net.minecraft.server.network.ServerPlayerEntity;

/** Common-side entry points for CPM; forwards to client via ZeroSuitNet. */
public final class ZeroSuitCPM {
    private ZeroSuitCPM() {}

    public static void playStance(ServerPlayerEntity p)     { ZeroSuitNet.broadcastAnim(p, "stance_on"); }
    public static void stopStance(ServerPlayerEntity p)     { ZeroSuitNet.broadcastAnim(p, "stance_off"); }

    public static void playBlastCharge(ServerPlayerEntity p){ ZeroSuitNet.broadcastAnim(p, "charge_on"); }
    public static void stopBlastCharge(ServerPlayerEntity p){ ZeroSuitNet.broadcastAnim(p, "charge_off"); }

    public static void playForcePush(ServerPlayerEntity p)  { ZeroSuitNet.broadcastAnim(p, "force_push"); }
    public static void playForcePull(ServerPlayerEntity p)  { ZeroSuitNet.broadcastAnim(p, "force_pull"); }

    public static void playBlastFire(ServerPlayerEntity p)  { ZeroSuitNet.broadcastAnim(p, "blast_fire"); }
}
