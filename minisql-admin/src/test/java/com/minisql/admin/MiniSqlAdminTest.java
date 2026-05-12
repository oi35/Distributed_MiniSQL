package com.minisql.admin;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.Permission;

import static org.junit.Assert.*;

public class MiniSqlAdminTest {

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private SecurityManager originalSecurityManager;

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        // Install a SecurityManager that traps System.exit
        originalSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkExit(int status) {
                throw new SecurityException("Blocking System.exit(" + status + ") in test");
            }
            @Override
            public void checkPermission(Permission perm) {
                // Allow all other permissions
            }
        });
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setSecurityManager(originalSecurityManager);
    }

    @Test
    public void testHelpOutput() {
        try {
            MiniSqlAdmin.main(new String[]{"--help"});
        } catch (SecurityException e) {
            // Expected from System.exit interception
        }
        String output = out.toString();
        assertTrue(output.contains("MiniSQL Admin CLI"));
        assertTrue(output.contains("cluster status"));
        assertTrue(output.contains("table list"));
        assertTrue(output.contains("--host"));
    }

    @Test
    public void testShortHelpFlag() {
        try {
            MiniSqlAdmin.main(new String[]{"-h"});
        } catch (SecurityException e) {
        }
        String output = out.toString();
        assertTrue(output.contains("MiniSQL Admin CLI"));
    }

    @Test
    public void testNoArgsShowsHelp() {
        try {
            MiniSqlAdmin.main(new String[]{});
        } catch (SecurityException e) {
        }
        String output = out.toString();
        assertTrue(output.contains("MiniSQL Admin CLI"));
    }

    @Test
    public void testUnknownOptionExitsWithError() {
        try {
            MiniSqlAdmin.main(new String[]{"--unknown-option", "cluster", "status"});
        } catch (SecurityException e) {
        }
        String errorOutput = err.toString();
        assertTrue(errorOutput.contains("Unknown option"));
    }

    @Test
    public void testUnknownCommand() {
        try {
            MiniSqlAdmin.main(new String[]{"invalid_command"});
        } catch (SecurityException e) {
        }
        String errorOutput = err.toString();
        assertTrue(errorOutput.contains("Unknown command"));
    }
}
