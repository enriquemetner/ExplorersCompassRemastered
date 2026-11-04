package com.chaosthedude.explorerscompass.workers;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearestFirstQueueTest {
    record Point(int grid, int x, int z) {}
    private static long distance(long x, long z, long ox, long oz) {
        return (x-ox)*(x-ox)+(z-oz)*(z-oz);
    }
    @Test void matchesExhaustiveSearchAcrossMixedGridsAndNegativeCoordinates() {
        Random random = new Random(143);
        for (int trial = 0; trial < 80; trial++) {
            int ox = random.nextInt(3000)-1500, oz = random.nextInt(3000)-1500;
            var queue = new NearestFirstQueue<Point>(ox, oz);
            Map<Point, Long> expected = new HashMap<>();
            Set<Point> calculated = new HashSet<>();
            int radius = 1200;
            for (int grid = 0; grid < 3; grid++) {
                int id = grid, step = new int[]{512,384,16}[grid];
                int span = grid == 2 ? 0 : step-160;
                int dx = grid*11-9, dz = 7-grid*17;
                java.util.function.BiFunction<Integer,Integer,NearestFirstQueue.Candidate<Point>> make = (x,z) -> {
                    Random seeded = new Random(x*34567L+z*234L+id);
                    return new NearestFirstQueue.Candidate<>((long)x*step+dx+seeded.nextInt(span+1),
                            (long)z*step+dz+seeded.nextInt(span+1), new Point(id,x,z));
                };
                queue.addGrid(step,span,dx,dz,(x,z) -> {
                    assertTrue(calculated.add(new Point(id,x,z)), "Each region calculated only once");
                    return make.apply(x,z);
                });
                for (int x=Math.floorDiv(ox-radius-dx,step)-1;x<=Math.floorDiv(ox+radius-dx,step)+1;x++)
                    for (int z=Math.floorDiv(oz-radius-dz,step)-1;z<=Math.floorDiv(oz+radius-dz,step)+1;z++) {
                        var c=make.apply(x,z); long d=distance(c.x(),c.z(),ox,oz);
                        if(d<(long)radius*radius) expected.put(c.value(),d);
                    }
            }
            long previous=-1;
            Set<Point> actual=new HashSet<>();
            while(queue.nextDistanceSquared()<(long)radius*radius) {
                var c=queue.advance();
                if(c==null) continue;
                long d=distance(c.x(),c.z(),ox,oz);
                assertTrue(d>=previous); previous=d;
                assertTrue(actual.add(c.value()));
            }
            assertEquals(expected.keySet(),actual);
        }
    }
    @Test void retainsOutsideCandidatesAndResumesWithoutRecalculation() {
        var queue=new NearestFirstQueue<String>(0,0);
        Set<String> seen=new HashSet<>();
        queue.addGrid(100,99,0,0,(x,z)-> {
            assertTrue(seen.add(x+","+z));
            return new NearestFirstQueue.Candidate<>(x*100L+90,z*100L+90,x+","+z);
        });
        List<String> results=new ArrayList<>();
        for(int radius:new int[]{50,100,250})
            while(queue.nextDistanceSquared()<(long)radius*radius) {
                var c=queue.advance(); if(c!=null) results.add(c.value());
            }
        assertEquals("-1,-1",results.get(0));
        assertTrue(results.contains("0,0"));
        assertEquals(results.size(),new HashSet<>(results).size());
    }
    @Test void mixesFiniteCandidatesAndGridsAndAdvancesAfterAllKnownFailures() {
        var queue=new NearestFirstQueue<String>(0,0);
        queue.addCandidate(new NearestFirstQueue.Candidate<>(30,0,"rings"));
        queue.addGrid(100,0,0,0,(x,z)->new NearestFirstQueue.Candidate<>(x*100L,z*100L,"grid"));
        List<Long> distances=new ArrayList<>();
        while(distances.size()<10) {
            var c=queue.advance(); if(c!=null) distances.add(distance(c.x(),c.z(),0,0));
        }
        assertEquals(List.of(0L,900L,10000L,10000L,10000L,10000L,20000L,20000L,20000L,20000L),distances);
    }
    @Test void exactTiesAndRadiusBoundaryAreRetained() {
        var queue=new NearestFirstQueue<String>(0,0);
        queue.addCandidate(new NearestFirstQueue.Candidate<>(3,4,"a"));
        queue.addCandidate(new NearestFirstQueue.Candidate<>(-3,4,"b"));
        assertEquals(25,queue.nextDistanceSquared());
        assertEquals("a",queue.advance().value());
        assertEquals("b",queue.advance().value());
        assertTrue(queue.isEmpty());
    }
    @Test void checksCloserDiscoveryWithoutPreparingOriginalCandidatesWholeCircle() {
        var queue = new NearestFirstQueue<String>(0,0);
        int[] calculated = {0};
        queue.addGrid(512,511,0,0,(x,z) -> {
            calculated[0]++;
            long px = x == -1 ? -1 : x*512L+400;
            long pz = z == 0 ? 0 : z*512L+400;
            if(x == 0 && z == 0) pz = 400;
            return new NearestFirstQueue.Candidate<>(px,pz,x+","+z);
        });
        NearestFirstQueue.Candidate<String> first;
        do { first = queue.advance(); } while(first == null);
        assertEquals("-1,0",first.value());
        assertTrue(calculated[0] <= 3, "Check the closer discovery before filling the original large circle");
    }
    @Test void handlesWorldEdgeCoordinatesWithoutDistanceOverflow() {
        var queue = new NearestFirstQueue<String>(-29999999,29999999);
        queue.addCandidate(new NearestFirstQueue.Candidate<>(29999999,-29999999,"far"));
        queue.addCandidate(new NearestFirstQueue.Candidate<>(-29999998,29999999,"near"));
        assertEquals("near",queue.advance().value());
        assertTrue(queue.nextDistanceSquared() > Integer.MAX_VALUE);
        assertEquals("far",queue.advance().value());
    }
}
