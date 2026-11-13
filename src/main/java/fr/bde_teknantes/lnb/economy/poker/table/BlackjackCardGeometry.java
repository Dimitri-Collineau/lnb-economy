/*
 * From MineVegas Casino 1.1.1 by byMehd (https://www.curseforge.com/minecraft/mc-mods/minevegas-casino), under its
 * licence: MIT with an attribution condition, see LICENSES/MineVegas-Casino.txt. Decompiled, moved to this package,
 * otherwise unchanged unless a comment says so.
 */
package fr.bde_teknantes.lnb.economy.poker.table;

import java.util.function.Consumer;

public final class BlackjackCardGeometry {
   public static final int CELL_W = 25;
   public static final int CELL_H = 32;
   public static final int SHEET_W = 350;
   public static final int SHEET_H = 128;
   public static final int BACK_COLUMN = 13;
   private static final double HALF_THICKNESS = 0.03;

   private BlackjackCardGeometry() {
   }

   private static double[][] cellUv(int column, int row) {
      double inset = 0.02;
      double u0 = (column * 25 + inset) / 350.0;
      double u1 = ((column + 1) * 25 - inset) / 350.0;
      double v0 = (row * 32 + inset) / 128.0;
      double v1 = ((row + 1) * 32 - inset) / 128.0;
      return new double[][]{{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
   }

   public static void quads(BlackjackCardMotion.Pose pose, int value, Consumer<BlackjackCardGeometry.Quad> sink) {
      double s = pose.scale;
      double w = 2.5 * s;
      double l = 3.2 * s;
      double vx = Math.cos(pose.yaw);
      double vz = Math.sin(pose.yaw);
      double[] u = new double[]{-vz, 0.0, vx};
      double[] v = new double[]{vx, 0.0, vz};
      double[] n = new double[]{0.0, 1.0, 0.0};
      double cf = Math.cos(pose.flip);
      double sf = Math.sin(pose.flip);
      double[] vf = new double[]{v[0] * cf + n[0] * sf, v[1] * cf + n[1] * sf, v[2] * cf + n[2] * sf};
      double[] nf = new double[]{-v[0] * sf + n[0] * cf, -v[1] * sf + n[1] * cf, -v[2] * sf + n[2] * cf};
      int column = value == -1 ? 13 : BlackjackCards.rank(value);
      int row = value == -1 ? 0 : BlackjackCards.suit(value);
      double[][] faceUv = cellUv(column, row);
      double[][] backUv = cellUv(13, 0);
      double[][] corner = new double[][]{{-w, l}, {-w, -l}, {w, -l}, {w, l}};

      for (int side = 0; side < 2; side++) {
         BlackjackCardGeometry.Quad q = new BlackjackCardGeometry.Quad();
         double sign = side == 0 ? 1.0 : -1.0;

         for (int i = 0; i < 4; i++) {
            int source = side == 0 ? i : new int[]{3, 2, 1, 0}[i];
            double cu = corner[source][0];
            double cv = corner[source][1];
            double px = cu * u[0] + cv * vf[0] + sign * 0.03 * nf[0];
            double py = cu * u[1] + cv * vf[1] + sign * 0.03 * nf[1];
            double pz = cu * u[2] + cv * vf[2] + sign * 0.03 * nf[2];
            if (pose.lift != 0.0) {
               double hz = 3.2 * s;
               double ry = py;
               double rz = pz - hz;
               double ca = Math.cos(pose.lift);
               double sa = Math.sin(pose.lift);
               py = py * ca - rz * sa;
               pz = ry * sa + rz * ca + hz;
            }

            q.v[i][0] = pose.x + px;
            q.v[i][1] = pose.y + py;
            q.v[i][2] = pose.z + pz;
            double[][] uv = side == 0 ? faceUv : backUv;
            q.uv[i][0] = uv[source][0];
            q.uv[i][1] = uv[source][1];
         }

         q.normal[0] = sign * nf[0];
         q.normal[1] = sign * nf[1];
         q.normal[2] = sign * nf[2];
         sink.accept(q);
      }
   }

   public static final class Quad {
      public final double[][] v = new double[4][3];
      public final double[][] uv = new double[4][2];
      public final double[] normal = new double[3];
   }
}
