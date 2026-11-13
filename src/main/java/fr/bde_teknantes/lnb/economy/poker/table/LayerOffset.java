/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

import com.mojang.blaze3d.vertex.PoseStack;

public final class LayerOffset {
   private static final float[] PROUD = new float[]{0.0F, 0.03F, 0.06F, 0.09F};
   private static final float PIVOT_Y = 10.0F;
   private static final float HALF_WIDTH = 4.0F;
   private static final float HALF_HEIGHT = 10.0F;

   private LayerOffset() {
   }

   static float[] scales(int order) {
      float step = order >= 0 && order < PROUD.length ? PROUD[order] : PROUD[PROUD.length - 1];
      return new float[]{1.0F + step / 4.0F, 1.0F + step / 10.0F};
   }

   public static PoseStack of(PoseStack pose, int order) {
      if (order <= 0) {
         return pose;
      } else {
         float[] k = scales(order);
         PoseStack out = new PoseStack();
         out.last().pose().set(pose.last().pose());
         out.last().normal().set(pose.last().normal());
         out.translate(0.0F, 0.625F, 0.0F);
         out.scale(k[0], k[1], k[0]);
         out.translate(0.0F, -0.625F, 0.0F);
         return out;
      }
   }
}
