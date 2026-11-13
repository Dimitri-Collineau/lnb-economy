/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

public final class DealerIdleAnimation {
   public static final float LOOP = 480.0F;
   private static final float TAU = (float) (Math.PI * 2);
   static final float REST_TORSO_LEAN = 0.035F;
   static final float REST_SHOULDER_X = -0.3F;
   static final float REST_SHOULDER_Y = 0.04F;
   static final float REST_SHOULDER_Z = 0.07F;
   static final float REST_ELBOW_X = -1.05F;
   static final float REST_HEAD_X = 0.12F;

   private DealerIdleAnimation() {
   }

   static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   static float smooth(float v) {
      v = clamp01(v);
      return v * v * (3.0F - 2.0F * v);
   }

   static float window(float t, float start, float end, float fade) {
      return smooth((t - start) / fade) * smooth((end - t) / fade);
   }

   static float track(float t, float[] times, float[] values) {
      if (t <= times[0]) {
         return values[0];
      } else {
         for (int i = 1; i < times.length; i++) {
            if (t <= times[i]) {
               float k = smooth((t - times[i - 1]) / (times[i] - times[i - 1]));
               return values[i - 1] + (values[i] - values[i - 1]) * k;
            }
         }

         return values[values.length - 1];
      }
   }

   private static float sin(float v) {
      return (float)Math.sin(v);
   }

   public static void evaluate(float ticks, DealerIdleAnimation.Pose p) {
      float t = (ticks % 480.0F + 480.0F) % 480.0F;
      float breath = sin((float) (Math.PI * 2) * ticks / 90.0F);
      p.torsoX = 0.0F;
      p.torsoY = -breath * 0.18F;
      p.torsoXRot = 0.035F - breath * 0.008F;
      p.torsoYRot = 0.0F;
      p.torsoZRot = 0.0F;
      p.headYRot = track(
         t,
         new float[]{0.0F, 55.0F, 95.0F, 140.0F, 175.0F, 240.0F, 285.0F, 312.0F, 340.0F, 380.0F, 440.0F, 480.0F},
         new float[]{0.0F, 0.0F, -0.42F, -0.42F, 0.03F, 0.03F, 0.46F, 0.03F, 0.0F, 0.0F, -0.06F, 0.0F}
      );
      p.headXRot = 0.12F
         - breath * 0.012F
         + track(
            t,
            new float[]{0.0F, 140.0F, 175.0F, 215.0F, 235.0F, 285.0F, 312.0F, 330.0F, 360.0F, 392.0F, 436.0F, 480.0F},
            new float[]{0.0F, 0.0F, 0.14F, 0.14F, 0.0F, 0.0F, 0.04F, 0.16F, 0.0F, 0.0F, 0.12F, 0.0F}
         );
      p.headZRot = p.headYRot * 0.05F;
      p.torsoYRot = p.headYRot * 0.2F;

      for (int i = 0; i < 2; i++) {
         float side = i == 0 ? 1.0F : -1.0F;
         p.shoulderXRot[i] = -0.3F + breath * 0.035F;
         p.shoulderYRot[i] = 0.04F * side - p.torsoYRot;
         p.shoulderZRot[i] = 0.07F * side;
         p.elbowXRot[i] = -1.05F;
         p.legX[i] = 0.0F;
         p.legZRot[i] = 0.0F;
      }

      p.elbowXRot[1] = p.elbowXRot[1] + 0.02F * sin((float) (Math.PI * 2) * t / 120.0F);
      p.shoulderYRot[1] = p.shoulderYRot[1] + 0.015F * sin((float) (Math.PI * 2) * t / 96.0F + 1.0F);
      float g1 = window(t, 150.0F, 235.0F, 20.0F);
      if (g1 > 0.0F) {
         float sweep = sin((float) (Math.PI * 2) * (t - 150.0F) / 38.0F);
         p.shoulderYRot[0] = p.shoulderYRot[0] + g1 * (0.12F + 0.46F * sweep);
         p.torsoYRot += g1 * sweep * 0.04F;
      }

      float shift = window(t, 236.0F, 300.0F, 24.0F);
      p.torsoZRot += shift * 0.035F;
      p.torsoX += shift * 0.5F;
      p.legZRot[1] = p.legZRot[1] - shift * 0.03F;
      float shift2 = window(t, 438.0F, 478.0F, 14.0F);
      p.torsoZRot -= shift2 * 0.03F;
      p.torsoX -= shift2 * 0.4F;
      float g2 = window(t, 296.0F, 362.0F, 22.0F);
      if (g2 > 0.0F) {
         float tweak = sin((float) (Math.PI * 2) * (t - 296.0F) / 26.0F);
         p.shoulderXRot[0] = p.shoulderXRot[0] + g2 * -0.65F;
         p.shoulderYRot[0] = p.shoulderYRot[0] + g2 * -0.66F;
         p.shoulderZRot[0] = p.shoulderZRot[0] + g2 * 0.35F;
         p.elbowXRot[0] = p.elbowXRot[0] + g2 * (-0.6F + 0.07F * tweak);
         p.shoulderYRot[0] = p.shoulderYRot[0] + g2 * 0.04F * tweak;
      }

      float g3 = window(t, 392.0F, 438.0F, 6.0F);
      if (g3 > 0.0F) {
         float phase = (t - 392.0F) % 20.0F / 20.0F;
         float lift = phase < 0.62F ? smooth(phase / 0.62F) : 1.0F - smooth((phase - 0.62F) / 0.12F);
         p.shoulderXRot[1] = p.shoulderXRot[1] + g3 * -0.05F * lift;
         p.elbowXRot[1] = p.elbowXRot[1] - g3 * 0.45F * lift;
      }
   }

   public static final class Pose {
      public float torsoX;
      public float torsoY;
      public float torsoXRot;
      public float torsoYRot;
      public float torsoZRot;
      public float headXRot;
      public float headYRot;
      public float headZRot;
      public final float[] shoulderXRot = new float[2];
      public final float[] shoulderYRot = new float[2];
      public final float[] shoulderZRot = new float[2];
      public final float[] elbowXRot = new float[2];
      public final float[] legX = new float[2];
      public final float[] legZRot = new float[2];
   }
}
