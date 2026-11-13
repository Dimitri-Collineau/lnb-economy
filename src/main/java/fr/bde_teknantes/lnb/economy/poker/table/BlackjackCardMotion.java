/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

import java.util.List;

public final class BlackjackCardMotion {
   public static final float GRAB = 7.0F;
   public static final float FLIP_START = 4.0F;
   public static final float FLIP_END = 12.0F;
   public static final float PEEK_UP_START = 5.0F;
   public static final float PEEK_UP_END = 10.0F;
   public static final float PEEK_DOWN_START = 22.0F;
   public static final float PEEK_DOWN_END = 27.0F;
   public static final float CLEAR_START = 7.5F;
   public static final float CLEAR_SWEEP = 9.0F;
   public static final int SHUFFLE_CARDS = 7;

   private BlackjackCardMotion() {
   }

   private static double smooth(double v) {
      v = v < 0.0 ? 0.0 : Math.min(v, 1.0);
      return v * v * (3.0 - 2.0 * v);
   }

   private static double angleLerp(double a, double b, double t) {
      double d = ((b - a + Math.PI) % (Math.PI * 2) + (Math.PI * 2)) % (Math.PI * 2) - Math.PI;
      return a + d * t;
   }

   public static boolean pose(BlackjackCardMotion.Card c, double now, BlackjackCardMotion.Pose out) {
      return pose(c, BlackjackLayout.slot(c.toDealer, c.spot, c.index), now, out);
   }

   public static boolean pose(BlackjackCardMotion.Card c, BlackjackLayout.Slot slot, double now, BlackjackCardMotion.Pose out) {
      double t = now - c.dealtAt;
      if (t < 7.0) {
         return false;
      } else {
         out.scale = 1.0;
         out.lift = 0.0;
         out.visible = true;
         double x;
         double y;
         double z;
         double yaw;
         double flip;
         if (t < 23.0) {
            double[] hold = new double[5];
            held(slot, Math.min(t, 14.0), hold);
            if (t < 14.0) {
               x = hold[0];
               y = hold[1];
               z = hold[2];
               yaw = hold[3];
               flip = Math.PI;
            } else {
               double tau = (t - 14.0) / 9.0;
               double e = 1.0 - Math.pow(1.0 - tau, 2.2);
               x = hold[0] + (slot.x() - hold[0]) * e;
               z = hold[2] + (slot.z() - hold[2]) * e;
               y = hold[1] + (slot.y() - hold[1]) * e + 1.5 * Math.sin(Math.PI * tau) * (1.0 - tau * 0.4);
               yaw = angleLerp(hold[3], slot.yaw(), smooth(tau * 1.15));
               flip = c.faceUp ? Math.PI * (1.0 - smooth((tau - 0.05) / 0.8)) : Math.PI;
            }
         } else {
            x = slot.x();
            y = slot.y();
            z = slot.z();
            yaw = slot.yaw();
            flip = c.faceUp ? 0.0 : Math.PI;
         }

         if (c.flipAt >= 0L) {
            double tf = now - c.flipAt;
            double s = smooth((tf - 4.0) / 8.0);
            if (tf >= 12.0) {
               flip = 0.0;
            } else if (tf > 4.0) {
               flip = Math.PI * (1.0 - s);
               y += 2.4 * Math.sin(Math.PI * s);
            }
         }

         if (c.peekAt >= 0L) {
            double tp = now - c.peekAt;
            double up = smooth((tp - 5.0) / 5.0);
            double down = smooth((tp - 22.0) / 5.0);
            out.lift = 0.62 * up * (1.0 - down);
         }

         if (c.clearAt >= 0L) {
            double tc = now - c.clearAt - 7.5 - 0.5 * c.index - (c.toDealer ? 0 : 1);
            if (tc > 0.0) {
               double s = smooth(tc / 9.0);
               if (s >= 1.0) {
                  return false;
               }

               x += (32.0 - x) * s;
               z += (3.4 - z) * s;
               y += 0.7 * Math.sin(Math.PI * s);
               out.scale = 1.0 - 0.25 * s;
            }
         }

         out.x = x;
         out.y = y;
         out.z = z;
         out.yaw = yaw;
         out.flip = flip;
         return true;
      }
   }

   public static void shuffleDeck(double t, List<BlackjackCardMotion.Pose> out) {
      out.clear();
      double length = DealerPlayAnimation.duration(DealerPlayAnimation.Kind.SHUFFLE);
      if (!(t < 3.0) && !(t > length - 2.0)) {
         DealerPlayAnimation.Input input = new DealerPlayAnimation.Input();
         input.kind = DealerPlayAnimation.Kind.SHUFFLE;
         input.time = (float)t;
         DealerIdleAnimation.Pose pose = new DealerIdleAnimation.Pose();
         DealerPlayAnimation.gesturePose(input, pose);
         DealerKinematics.Hand right = DealerKinematics.hand(pose, 0);
         DealerKinematics.Hand left = DealerKinematics.hand(pose, 1);
         double appear = smooth((t - 3.0) / 3.0) * (1.0 - smooth((t - (length - 5.0)) / 3.0));
         double midX = (right.tip().x() + left.tip().x()) / 2.0;
         double midZ = (right.tip().z() + left.tip().z()) / 2.0 + 0.5;
         double baseY = Math.max(Math.min(right.tip().y(), left.tip().y()) - 1.3, 13.9);
         double tilt = 0.45 * (left.tip().z() - right.tip().z()) / 5.0;

         for (int i = 0; i < 7; i++) {
            BlackjackCardMotion.Pose p = new BlackjackCardMotion.Pose();
            p.x = midX + 0.5 * Math.sin(t * 1.9 + i * 1.7) * appear;
            p.z = midZ + 0.4 * Math.cos(t * 1.5 + i * 2.3) * appear;
            p.y = baseY + 0.21 * i;
            p.yaw = (-Math.PI / 2) + tilt + 0.07 * Math.sin(t * 2.1 + i * 1.3);
            p.flip = Math.PI;
            p.scale = appear;
            p.visible = appear > 0.02;
            out.add(p);
         }
      }
   }

   static void held(BlackjackCardMotion.Card c, double t, double[] out) {
      held(BlackjackLayout.slot(c.toDealer, c.spot, c.index), t, out);
   }

   static void held(BlackjackLayout.Slot slot, double t, double[] out) {
      DealerPlayAnimation.Input input = new DealerPlayAnimation.Input();
      input.kind = DealerPlayAnimation.Kind.DEAL;
      input.time = (float)t;
      input.bearing = (float)BlackjackLayout.bearing(slot.x(), slot.z());
      DealerIdleAnimation.Pose pose = new DealerIdleAnimation.Pose();
      DealerPlayAnimation.gesturePose(input, pose);
      DealerKinematics.Hand hand = DealerKinematics.hand(pose, 1);
      double[] dir = new double[]{hand.pointing()[0], hand.pointing()[2]};
      double n = Math.hypot(dir[0], dir[1]);
      if (n < 1.0E-6) {
         dir[0] = 0.0;
         dir[1] = 1.0;
         n = 1.0;
      }

      dir[0] /= n;
      dir[1] /= n;
      double ahead = 1.8000000000000003;
      double yawHand = Math.atan2(dir[1], dir[0]);
      double yawShoe = -Math.PI / 2;
      double s = smooth((t - 7.0) / 3.0);
      out[0] = hand.tip().x() + dir[0] * ahead * s;
      out[2] = hand.tip().z() + dir[1] * ahead * s;
      out[1] = hand.tip().y() - 0.9;
      out[3] = angleLerp(yawShoe, yawHand, s);
      if (t < 10.0) {
         double k = smooth((t - 7.0) / 3.0);
         out[0] = 50.5 + (out[0] - 50.5) * k;
         out[1] = 15.3 + (out[1] - 15.3) * k;
         out[2] = 3.0 + (out[2] - 3.0) * k;
      }
   }

   public static final class Card {
      public boolean toDealer;
      public int index;
      public int value = -1;
      public boolean faceUp = true;
      public long dealtAt;
      public long flipAt = -1L;
      public long peekAt = -1L;
      public long clearAt = -1L;
      public int spot = 2;
   }

   public static final class Pose {
      public double x;
      public double y;
      public double z;
      public double yaw;
      public double flip;
      public double lift;
      public double scale = 1.0;
      public boolean visible;
   }
}
