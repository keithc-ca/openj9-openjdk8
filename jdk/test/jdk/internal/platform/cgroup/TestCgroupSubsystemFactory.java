/*
 * Copyright (c) 2020, Red Hat Inc.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jdk.internal.platform.CgroupSubsystemFactory;
import jdk.internal.platform.CgroupSubsystemFactory.CgroupTypeResult;
import jdk.testlibrary.Utils;
import jdk.testlibrary.FileUtils;


/*
 * @test
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @library /lib/testlibrary
 * @run junit/othervm TestCgroupSubsystemFactory
 */
public class TestCgroupSubsystemFactory {

    private Path existingDirectory;
    private Path cgroupv1CgroupsJoinControllers;
    private Path cgroupv1MountInfoJoinControllers;
    private Path cgroupv1CgInfoZeroHierarchy;
    private Path cgroupv1MntInfoZeroHierarchy;
    private Path cgroupv2CgInfoZeroHierarchy;
    private Path cgroupv2MntInfoZeroHierarchy;
    private Path cgroupv1CgInfoNonZeroHierarchy;
    private Path cgroupv1MntInfoNonZeroHierarchy;
    private Path cgroupv1MntInfoSystemdOnly;
    private Path cgroupv1MntInfoDoubleCpusets;
    private Path cgroupv1MntInfoDoubleCpusets2;
    private String mntInfoEmpty = "";
    private String cgroupsNonZeroJoinControllers =
            "#subsys_name hierarchy num_cgroups enabled\n" +
            "cpuset\t3\t1\t1\n" +
            "cpu\t4\t153\t1\n" +
            "cpuacct\t4\t153\t1\n" +
            "blkio\t7\t87\t1\n" +
            "memory\t4\t153\t1\n" +
            "devices\t6\t87\t1\n" +
            "freezer\t9\t1\t1\n" +
            "net_cls\t4\t153\t1\n" +
            "perf_event\t2\t1\t1\n" +
            "net_prio\t4\t153\t1\n" +
            "hugetlb\t4\t153\t1\n" +
            "pids\t5\t95\t1\n" +
            "rdma\t8\t1\t1\n";
    private String cgroupsZeroHierarchy =
            "#subsys_name hierarchy num_cgroups enabled\n" +
            "cpuset 0 1 1\n" +
            "cpu 0 1 1\n" +
            "cpuacct 0 1 1\n" +
            "memory 0 1 1\n" +
            "devices 0 1 1\n" +
            "freezer 0 1 1\n" +
            "net_cls 0 1 1\n" +
            "blkio 0 1 1\n" +
            "perf_event 0 1 1 ";
    private String mntInfoHybrid =
            "30 23 0:26 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:4 - tmpfs tmpfs ro,seclabel,mode=755\n" +
            "31 30 0:27 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:5 - cgroup2 none rw,seclabel,nsdelegate\n" +
            "32 30 0:28 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:6 - cgroup none rw,seclabel,xattr,name=systemd\n" +
            "35 30 0:31 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:7 - cgroup none rw,seclabel,memory\n" +
            "36 30 0:32 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:8 - cgroup none rw,seclabel,pids\n" +
            "37 30 0:33 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:9 - cgroup none rw,seclabel,perf_event\n" +
            "38 30 0:34 / /sys/fs/cgroup/net_cls,net_prio rw,nosuid,nodev,noexec,relatime shared:10 - cgroup none rw,seclabel,net_cls,net_prio\n" +
            "39 30 0:35 / /sys/fs/cgroup/hugetlb rw,nosuid,nodev,noexec,relatime shared:11 - cgroup none rw,seclabel,hugetlb\n" +
            "40 30 0:36 / /sys/fs/cgroup/cpu,cpuacct rw,nosuid,nodev,noexec,relatime shared:12 - cgroup none rw,seclabel,cpu,cpuacct\n" +
            "41 30 0:37 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:13 - cgroup none rw,seclabel,devices\n" +
            "42 30 0:38 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:14 - cgroup none rw,seclabel,cpuset\n" +
            "43 30 0:39 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:15 - cgroup none rw,seclabel,blkio\n" +
            "44 30 0:40 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:16 - cgroup none rw,seclabel,freezer\n";
    private String mntInfoCgroupv1JoinControllers =
            "31 22 0:26 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:9 - tmpfs tmpfs ro,mode=755\n" +
            "32 31 0:27 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:10 - cgroup2 cgroup2 rw,nsdelegate\n" +
            "33 31 0:28 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:11 - cgroup cgroup rw,xattr,name=systemd\n" +
            "36 31 0:31 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:15 - cgroup cgroup rw,perf_event\n" +
            "37 31 0:32 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,cpuset\n" +
            "38 31 0:33 / /sys/fs/cgroup/cpu,cpuacct,net_cls,net_prio,hugetlb,memory rw,nosuid,nodev,noexec,relatime shared:17 - cgroup cgroup rw,cpu,cpuacct,memory,net_cls,net_prio,hugetlb\n" +
            "39 31 0:34 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:18 - cgroup cgroup rw,pids\n" +
            "40 31 0:35 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:19 - cgroup cgroup rw,devices\n" +
            "41 31 0:36 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:20 - cgroup cgroup rw,blkio\n" +
            "42 31 0:37 / /sys/fs/cgroup/rdma rw,nosuid,nodev,noexec,relatime shared:21 - cgroup cgroup rw,rdma\n" +
            "43 31 0:38 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:22 - cgroup cgroup rw,freezer\n";
    private String cgroupsNonZeroHierarchy =
            "#subsys_name hierarchy   num_cgroups enabled\n" +
            "cpuset  9   1   1\n" +
            "cpu 7   1   1\n" +
            "cpuacct 7   1   1\n" +
            "blkio   10  1   1\n" +
            "memory  2   90  1\n" +
            "devices 8   74  1\n" +
            "freezer 11  1   1\n" +
            "net_cls 5   1   1\n" +
            "perf_event  4   1   1\n" +
            "net_prio    5   1   1\n" +
            "hugetlb 6   1   1\n" +
            "pids    3   80  1";
    private String mntInfoCgroupsV2Only =
            "28 21 0:25 / /sys/fs/cgroup rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 none rw,seclabel,nsdelegate";
    private String mntInfoCgroupsV1SystemdOnly =
            "35 26 0:26 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime - cgroup systemd rw,name=systemd\n" +
            "26 18 0:19 / /sys/fs/cgroup rw,relatime - tmpfs none rw,size=4k,mode=755\n";
    private String mntInfoCgroupv1MoreCpusetLine = "121 32 0:37 / /cpuset rw,relatime shared:69 - cgroup none rw,cpuset\n";
    private String mntInfoCgroupsV1DoubleCpuset = mntInfoHybrid + mntInfoCgroupv1MoreCpusetLine;
    private String mntInfoCgroupsV1DoubleCpuset2 = mntInfoCgroupv1MoreCpusetLine + mntInfoHybrid;

    @Before
    public void setup() {
        try {
            existingDirectory = Utils.createTempDirectory(TestCgroupSubsystemFactory.class.getSimpleName());
            Path cgroupsZero = Paths.get(existingDirectory.toString(), "cgroups_zero");
            Files.write(cgroupsZero, cgroupsZeroHierarchy.getBytes(StandardCharsets.UTF_8));
            cgroupv1CgInfoZeroHierarchy = cgroupsZero;
            cgroupv2CgInfoZeroHierarchy = cgroupsZero;
            cgroupv1MntInfoZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_empty");
            Files.write(cgroupv1MntInfoZeroHierarchy, mntInfoEmpty.getBytes());

            cgroupv2MntInfoZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv2");
            Files.write(cgroupv2MntInfoZeroHierarchy, mntInfoCgroupsV2Only.getBytes());

            cgroupv1CgInfoNonZeroHierarchy = Paths.get(existingDirectory.toString(), "cgroups_non_zero");
            Files.write(cgroupv1CgInfoNonZeroHierarchy, cgroupsNonZeroHierarchy.getBytes());

            cgroupv1MntInfoNonZeroHierarchy = Paths.get(existingDirectory.toString(), "mountinfo_non_zero");
            Files.write(cgroupv1MntInfoNonZeroHierarchy, mntInfoHybrid.getBytes());

            cgroupv1MntInfoSystemdOnly = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv1_systemd_only");
            Files.write(cgroupv1MntInfoSystemdOnly, mntInfoCgroupsV1SystemdOnly.getBytes());

            cgroupv1MntInfoDoubleCpusets = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv1_double_cpuset");
            Files.write(cgroupv1MntInfoDoubleCpusets, mntInfoCgroupsV1DoubleCpuset.getBytes());

            cgroupv1MntInfoDoubleCpusets2 = Paths.get(existingDirectory.toString(), "mountinfo_cgroupv1_double_cpuset2");
            Files.write(cgroupv1MntInfoDoubleCpusets2, mntInfoCgroupsV1DoubleCpuset2.getBytes());

            cgroupv1CgroupsJoinControllers = Paths.get(existingDirectory.toString(), "cgroups_cgv1_join_controllers");
            Files.write(cgroupv1CgroupsJoinControllers, cgroupsNonZeroJoinControllers.getBytes());

            cgroupv1MountInfoJoinControllers = Paths.get(existingDirectory.toString(), "mntinfo_cgv1_join_controllers");
            Files.write(cgroupv1MountInfoJoinControllers, mntInfoCgroupv1JoinControllers.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void teardown() {
        try {
            FileUtils.deleteFileTreeWithRetry(existingDirectory);
        } catch (IOException e) {
            System.err.println("Teardown failed. " + e.getMessage());
        }
    }

    @Test
    public void testCgroupv1JoinControllerCombo() throws IOException {
        String cgroups = cgroupv1CgroupsJoinControllers.toString();
        String mountInfo = cgroupv1MountInfoJoinControllers.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("Expected non-empty cgroup result", result.isPresent());
        CgroupTypeResult res = result.get();
        assertFalse("Join controller combination expected as cgroups v1", res.isCgroupV2());
    }

    @Test
    public void testCgroupv1SystemdOnly() throws IOException {
        String cgroups = cgroupv1CgInfoZeroHierarchy.toString();
        String mountInfo = cgroupv1MntInfoSystemdOnly.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("zero hierarchy ids with no *relevant* controllers mounted", Optional.empty().equals(result));
    }

    @Test
    public void testCgroupv1MultipleCpusetMounts() throws IOException {
        doMultipleCpusetMountsTest(cgroupv1MntInfoDoubleCpusets);
        doMultipleCpusetMountsTest(cgroupv1MntInfoDoubleCpusets2);
    }

    private void doMultipleCpusetMountsTest(Path info) throws IOException {
        String cgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String mountInfo = info.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("Expected non-empty cgroup result", result.isPresent());
        CgroupTypeResult res = result.get();
        assertFalse("Duplicate cpusets should not influence detection heuristic", res.isCgroupV2());
    }

    @Test
    public void testHybridCgroupsV1() throws IOException {
        String cgroups = cgroupv1CgInfoNonZeroHierarchy.toString();
        String mountInfo = cgroupv1MntInfoNonZeroHierarchy.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("Expected non-empty cgroup result", result.isPresent());
        CgroupTypeResult res = result.get();
        assertFalse("hybrid hierarchy expected as cgroups v1", res.isCgroupV2());
    }

    @Test
    public void testZeroHierarchyCgroupsV1() throws IOException {
        String cgroups = cgroupv1CgInfoZeroHierarchy.toString();
        String mountInfo = cgroupv1MntInfoZeroHierarchy.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("zero hierarchy ids with no mounted controllers => empty result", Optional.empty().equals(result));
    }

    @Test
    public void testZeroHierarchyCgroupsV2() throws IOException {
        String cgroups = cgroupv2CgInfoZeroHierarchy.toString();
        String mountInfo = cgroupv2MntInfoZeroHierarchy.toString();
        Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountInfo, cgroups);

        assertTrue("Expected non-empty cgroup result", result.isPresent());
        CgroupTypeResult res = result.get();

        assertTrue("zero hierarchy ids with mounted controllers expected cgroups v2", res.isCgroupV2());
    }

    @Test(expected = IOException.class)
    public void mountInfoFileNotFound() throws IOException {
        String cgroups = cgroupv1CgInfoZeroHierarchy.toString(); // any existing file
        String mountInfo = Paths.get(existingDirectory.toString(), "not-existing-mountinfo").toString();

        CgroupSubsystemFactory.determineType(mountInfo, cgroups);
    }

    @Test(expected = IOException.class)
    public void cgroupsFileNotFound() throws IOException {
        String cgroups = Paths.get(existingDirectory.toString(), "not-existing-cgroups").toString();
        String mountInfo = cgroupv2MntInfoZeroHierarchy.toString(); // any existing file
        CgroupSubsystemFactory.determineType(mountInfo, cgroups);
    }
}
