package com.example;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AggregationServerTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AggregationServerTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AggregationServerTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testAggregationServer()
    {
        assertTrue( true );
    }
}
