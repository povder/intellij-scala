package org.jetbrains.plugins.scala.lang.formatter.intellij.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.plugins.scala.lang.formatter.intellij.FormatterTestSuite;

public class ScalaDocFormatterTest extends TestCase {
    public static Test suite() {
        return new FormatterTestSuite("/formatter/scalaDocData/") {
        };
    }
}