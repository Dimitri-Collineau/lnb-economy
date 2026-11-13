/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

public final class DealerKinematics {
   public static final double DEALER_X = 32.0;
   public static final double BEHIND = 4.6;
   public static final double DEALER_Z = -4.6;
   public static final double SCALE = 0.9375;
   private static final double[] SHOULDER_RIGHT = new double[]{-6.0, -10.0, 0.0};
   private static final double[] SHOULDER_LEFT = new double[]{6.0, -10.0, 0.0};
   public static final double UPPER_ARM = 4.0;
   public static final double HAND_CENTER = 4.5;
   public static final double FINGER_TIP = 6.0;

   private DealerKinematics() {
   }

   static double[][] rotation(double x, double y, double z) {
      double cx = Math.cos(x);
      double sx = Math.sin(x);
      double cy = Math.cos(y);
      double sy = Math.sin(y);
      double cz = Math.cos(z);
      double sz = Math.sin(z);
      double[][] rx = new double[][]{{1.0, 0.0, 0.0}, {0.0, cx, -sx}, {0.0, sx, cx}};
      double[][] ry = new double[][]{{cy, 0.0, sy}, {0.0, 1.0, 0.0}, {-sy, 0.0, cy}};
      double[][] rz = new double[][]{{cz, -sz, 0.0}, {sz, cz, 0.0}, {0.0, 0.0, 1.0}};
      return mul(rz, mul(ry, rx));
   }

   static double[][] mul(double[][] a, double[][] b) {
      double[][] out = new double[3][3];

      for (int i = 0; i < 3; i++) {
         for (int j = 0; j < 3; j++) {
            out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
         }
      }

      return out;
   }

   static double[] apply(double[][] m, double[] v) {
      return new double[]{
         m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2], m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]
      };
   }

   static double[] add(double[] a, double[] b) {
      return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
   }

   public static DealerKinematics.Point toTable(double[] m) {
      return new DealerKinematics.Point(32.0 + 0.9375 * m[0], (24.0 - m[1]) * 0.9375, -4.6 - 0.9375 * m[2]);
   }

   public static double[] toModel(double x, double y, double z) {
      return new double[]{(x - 32.0) / 0.9375, 24.0 - y / 0.9375, -(z - -4.6) / 0.9375};
   }

   public static DealerKinematics.Hand hand(DealerIdleAnimation.Pose pose, int side) {
      double[][] torso = rotation(pose.torsoXRot, pose.torsoYRot, pose.torsoZRot);
      double[] pivot = new double[]{pose.torsoX, 12.0 + pose.torsoY, 0.0};
      double[] shoulder = add(pivot, apply(torso, side == 0 ? SHOULDER_RIGHT : SHOULDER_LEFT));
      double[][] arm = mul(torso, rotation(pose.shoulderXRot[side], pose.shoulderYRot[side], pose.shoulderZRot[side]));
      double[] elbow = add(shoulder, apply(arm, new double[]{0.0, 4.0, 0.0}));
      double[][] fore = mul(arm, rotation(pose.elbowXRot[side], 0.0, 0.0));
      double[] center = add(elbow, apply(fore, new double[]{0.0, 4.5, 0.0}));
      double[] tip = add(elbow, apply(fore, new double[]{0.0, 6.0, 0.0}));
      double[] pointing = apply(fore, new double[]{0.0, 1.0, 0.0});
      double[] dir = new double[]{pointing[0], -pointing[1], -pointing[2]};
      return new DealerKinematics.Hand(toTable(center), toTable(tip), dir, toTable(elbow), toTable(shoulder));
   }

   public static DealerKinematics.Point forearmCenter(DealerIdleAnimation.Pose pose, int side) {
      double[][] torso = rotation(pose.torsoXRot, pose.torsoYRot, pose.torsoZRot);
      double[] pivot = new double[]{pose.torsoX, 12.0 + pose.torsoY, 0.0};
      double[] shoulder = add(pivot, apply(torso, side == 0 ? SHOULDER_RIGHT : SHOULDER_LEFT));
      double[][] arm = mul(torso, rotation(pose.shoulderXRot[side], pose.shoulderYRot[side], pose.shoulderZRot[side]));
      double[] elbow = add(shoulder, apply(arm, new double[]{0.0, 4.0, 0.0}));
      double[][] fore = mul(arm, rotation(pose.elbowXRot[side], 0.0, 0.0));
      return toTable(add(elbow, apply(fore, new double[]{0.0, 3.0, 0.0})));
   }

   public record Hand(
      DealerKinematics.Point center, DealerKinematics.Point tip, double[] pointing, DealerKinematics.Point elbow, DealerKinematics.Point shoulder
   ) {
   }

   public record Point(double x, double y, double z) {
      public DealerKinematics.Point plus(double dx, double dy, double dz) {
         return new DealerKinematics.Point(this.x + dx, this.y + dy, this.z + dz);
      }

      public double distanceXZ(DealerKinematics.Point o) {
         return Math.hypot(this.x - o.x, this.z - o.z);
      }
   }
}
