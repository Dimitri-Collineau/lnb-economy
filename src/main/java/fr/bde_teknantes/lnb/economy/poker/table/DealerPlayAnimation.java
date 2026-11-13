/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

public final class DealerPlayAnimation {
   private DealerPlayAnimation() {
   }

   public static float duration(DealerPlayAnimation.Kind kind) {
      float[] times = times(kind);
      return times == null ? 0.0F : times[times.length - 1];
   }

   private static float[] times(DealerPlayAnimation.Kind kind) {
      return switch (kind) {
         case DEAL -> DealerPlayPoses.DEAL_T;
         case FLIP -> DealerPlayPoses.FLIP_T;
         case PEEK -> DealerPlayPoses.PEEK_T;
         case PAY -> DealerPlayPoses.PAY_T;
         case COLLECT -> DealerPlayPoses.COLLECT_T;
         case PUSH -> DealerPlayPoses.PUSH_T;
         case CELEBRATE -> DealerPlayPoses.CELEBRATE_T;
         case CLEAR -> DealerPlayPoses.CLEAR_T;
         case SHUFFLE -> DealerPlayPoses.SHUFFLE_T;
         default -> null;
      };
   }

   private static float[] weights(DealerPlayAnimation.Kind kind) {
      return switch (kind) {
         case DEAL -> DealerPlayPoses.DEAL_W;
         case FLIP -> DealerPlayPoses.FLIP_W;
         case PEEK -> DealerPlayPoses.PEEK_W;
         case PAY -> DealerPlayPoses.PAY_W;
         case COLLECT -> DealerPlayPoses.COLLECT_W;
         case PUSH -> DealerPlayPoses.PUSH_W;
         case CELEBRATE -> DealerPlayPoses.CELEBRATE_W;
         case CLEAR -> DealerPlayPoses.CLEAR_W;
         case SHUFFLE -> DealerPlayPoses.SHUFFLE_W;
         default -> null;
      };
   }

   private static float[][] keys(DealerPlayAnimation.Kind kind) {
      return switch (kind) {
         case DEAL -> DealerPlayPoses.DEAL;
         case FLIP -> DealerPlayPoses.FLIP;
         case PEEK -> DealerPlayPoses.PEEK;
         case PAY -> DealerPlayPoses.PAY;
         case COLLECT -> DealerPlayPoses.COLLECT;
         case PUSH -> DealerPlayPoses.PUSH;
         case CELEBRATE -> DealerPlayPoses.CELEBRATE;
         case CLEAR -> DealerPlayPoses.CLEAR;
         case SHUFFLE -> DealerPlayPoses.SHUFFLE;
         default -> null;
      };
   }

   private static float smooth(float v) {
      v = v < 0.0F ? 0.0F : Math.min(v, 1.0F);
      return v * v * (3.0F - 2.0F * v);
   }

   static void spline(float[] times, float[][] keys, float t, float[] out) {
      int n = times.length;
      if (t <= times[0]) {
         System.arraycopy(keys[0], 0, out, 0, out.length);
      } else if (t >= times[n - 1]) {
         System.arraycopy(keys[n - 1], 0, out, 0, out.length);
      } else {
         int i = 0;

         while (t > times[i + 1]) {
            i++;
         }

         float h = times[i + 1] - times[i];
         float s = (t - times[i]) / h;
         float s2 = s * s;
         float s3 = s2 * s;
         float h00 = 2.0F * s3 - 3.0F * s2 + 1.0F;
         float h10 = s3 - 2.0F * s2 + s;
         float h01 = -2.0F * s3 + 3.0F * s2;
         float h11 = s3 - s2;

         for (int c = 0; c < out.length; c++) {
            float p0 = keys[i][c];
            float p1 = keys[i + 1][c];
            float m0 = i > 0 ? (keys[i + 1][c] - keys[i - 1][c]) / (times[i + 1] - times[i - 1]) : (p1 - p0) / h;
            float m1 = i + 2 < n ? (keys[i + 2][c] - keys[i][c]) / (times[i + 2] - times[i]) : (p1 - p0) / h;
            out[c] = h00 * p0 + h10 * h * m0 + h01 * p1 + h11 * h * m1;
         }
      }
   }

   static float weightAt(float[] times, float[] weights, float t) {
      int n = times.length;
      if (t <= times[0]) {
         return weights[0];
      } else if (t >= times[n - 1]) {
         return weights[n - 1];
      } else {
         int i = 0;

         while (t > times[i + 1]) {
            i++;
         }

         float s = smooth((t - times[i]) / (times[i + 1] - times[i]));
         return weights[i] + (weights[i + 1] - weights[i]) * s;
      }
   }

   public static float keyPose(DealerPlayAnimation.Input in, float[] out) {
      float[] t = times(in.kind);
      if (t == null) {
         return 0.0F;
      } else {
         spline(t, keys(in.kind), in.time, out);
         if (in.kind == DealerPlayAnimation.Kind.DEAL) {
            float turn = smooth((in.time - 9.0F) / 5.0F);
            float bearing = Math.max(-1.1F, Math.min(1.1F, in.bearing));
            out[3] -= 0.34F * bearing * turn;
            out[6] = out[6] * (1.0F - turn) + -0.72F * bearing * turn;
            out[7] = out[6] * 0.05F;
         } else if (in.kind == DealerPlayAnimation.Kind.PAY || in.kind == DealerPlayAnimation.Kind.COLLECT || in.kind == DealerPlayAnimation.Kind.PUSH) {
            float length = t[t.length - 1];
            float look = smooth((in.time - 3.0F) / 4.0F) * (1.0F - smooth((in.time - (length - 8.0F)) / 6.0F));
            float bearing = Math.max(-1.1F, Math.min(1.1F, in.bearing));
            out[6] = out[6] * (1.0F - look) + -0.6F * bearing * look;
            out[7] = out[6] * 0.05F;
         }

         if (in.mirror) {
            mirror(out);
         }

         return weightAt(t, weights(in.kind), in.time);
      }
   }

   static void mirror(float[] v) {
      v[0] = -v[0];
      v[3] = -v[3];
      v[4] = -v[4];
      v[6] = -v[6];
      v[7] = -v[7];

      for (int c = 0; c < 4; c++) {
         float right = v[8 + c];
         float left = v[12 + c];
         boolean flips = c == 1 || c == 2;
         v[8 + c] = flips ? -left : left;
         v[12 + c] = flips ? -right : right;
      }
   }

   public static void apply(DealerPlayAnimation.Input in, DealerIdleAnimation.Pose pose) {
      if (in.kind != DealerPlayAnimation.Kind.NONE && times(in.kind) != null) {
         float[] v = new float[16];
         float w = keyPose(in, v);
         if (w > 0.0F) {
            blend(pose, v, w);
         }
      }

      if (in.watch > 0.0F) {
         watch(in, pose);
      }
   }

   public static void gesturePose(DealerPlayAnimation.Input in, DealerIdleAnimation.Pose out) {
      float[] v = new float[16];
      keyPose(in, v);
      out.torsoX = v[0];
      out.torsoY = v[1];
      out.torsoXRot = v[2];
      out.torsoYRot = v[3];
      out.torsoZRot = v[4];
      out.torsoXRot = v[2];
      out.headXRot = v[5];
      out.headYRot = v[6];
      out.headZRot = v[7];

      for (int side = 0; side < 2; side++) {
         int o = 8 + side * 4;
         out.shoulderXRot[side] = v[o];
         out.shoulderYRot[side] = v[o + 1];
         out.shoulderZRot[side] = v[o + 2];
         out.elbowXRot[side] = v[o + 3];
         out.legX[side] = 0.0F;
         out.legZRot[side] = 0.0F;
      }
   }

   private static float lerp(float a, float b, float w) {
      return a + (b - a) * w;
   }

   private static void blend(DealerIdleAnimation.Pose p, float[] v, float w) {
      p.torsoX = lerp(p.torsoX, v[0], w);
      p.torsoY = lerp(p.torsoY, v[1], w);
      p.torsoXRot = lerp(p.torsoXRot, v[2], w);
      p.torsoYRot = lerp(p.torsoYRot, v[3], w);
      p.torsoZRot = lerp(p.torsoZRot, v[4], w);
      p.headXRot = lerp(p.headXRot, v[5], w);
      p.headYRot = lerp(p.headYRot, v[6], w);
      p.headZRot = lerp(p.headZRot, v[7], w);

      for (int side = 0; side < 2; side++) {
         int o = 8 + side * 4;
         p.shoulderXRot[side] = lerp(p.shoulderXRot[side], v[o], w);
         p.shoulderYRot[side] = lerp(p.shoulderYRot[side], v[o + 1], w);
         p.shoulderZRot[side] = lerp(p.shoulderZRot[side], v[o + 2], w);
         p.elbowXRot[side] = lerp(p.elbowXRot[side], v[o + 3], w);
      }
   }

   private static void watch(DealerPlayAnimation.Input in, DealerIdleAnimation.Pose p) {
      float w = in.watch;
      float yaw = -Math.max(-0.7F, Math.min(0.7F, in.watchBearing * 0.8F));
      float glance = 0.5F + 0.5F * (float)Math.sin(in.watchTime * 2.0F * Math.PI / 110.0);
      float pitch = 0.04F + 0.2F * glance * glance;
      p.headYRot = lerp(p.headYRot, yaw + 0.03F * (float)Math.sin(in.watchTime * 0.13), w);
      p.headXRot = lerp(p.headXRot, pitch, w);
      p.headZRot = lerp(p.headZRot, p.headYRot * 0.05F, w);
      p.torsoYRot = lerp(p.torsoYRot, yaw * 0.25F, w);
      float cycle = in.watchTime % 150.0F;
      if (cycle > 95.0F && cycle < 123.0F) {
         float tap = Math.max(0.0F, (float)Math.sin((cycle - 95.0F) / 28.0F * Math.PI * 6.0));
         p.elbowXRot[0] = p.elbowXRot[0] - 0.15F * tap * w;
         p.shoulderXRot[0] = p.shoulderXRot[0] + 0.03F * tap * w;
      }
   }

   public static final class Input {
      public DealerPlayAnimation.Kind kind = DealerPlayAnimation.Kind.NONE;
      public float time;
      public float bearing;
      public float watch;
      public float watchBearing;
      public float watchTime;
      public boolean mirror;

      public void clear() {
         this.kind = DealerPlayAnimation.Kind.NONE;
         this.time = 0.0F;
         this.bearing = 0.0F;
         this.watch = 0.0F;
         this.watchBearing = 0.0F;
         this.watchTime = 0.0F;
         this.mirror = false;
      }
   }

   public static enum Kind {
      NONE,
      DEAL,
      FLIP,
      PEEK,
      PAY,
      COLLECT,
      PUSH,
      CELEBRATE,
      CLEAR,
      SHUFFLE;

      public static DealerPlayAnimation.Kind byOrdinal(int ordinal) {
         DealerPlayAnimation.Kind[] all = values();
         return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
      }
   }
}
