/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;

public final class DealerPlacement {
   public static final float BEHIND = 4.6F;
   public static final float SCALE = 0.9375F;

   private DealerPlacement() {
   }

   private static float baselineX() {
      return 0.5F;
   }

   private static float baselineZ() {
      return -1.7875F;
   }

   public static void apply(PoseStack pose, Direction facing) {
      float x = baselineX();
      float z = baselineZ();

      for (int i = 0; i < facing.get2DDataValue(); i++) {
         float nx = -z;
         float nz = x;
         x = nx;
         z = nz;
      }

      pose.translate(0.5F + x, 0.0F, 0.5F + z);
      pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
      pose.scale(0.9375F, 0.9375F, 0.9375F);
      pose.scale(-1.0F, -1.0F, 1.0F);
      pose.translate(0.0F, -1.501F, 0.0F);
   }
}
