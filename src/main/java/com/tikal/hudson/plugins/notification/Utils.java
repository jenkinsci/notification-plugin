package com.tikal.hudson.plugins.notification;

import java.util.Arrays;


/**
 * Helper utilities
 */
public final class Utils
{
    private Utils ()
    {
    }


    /**
     * Determines if any of Strings specified is either null or empty.
     */
    @SuppressWarnings( "MethodWithMultipleReturnPoints" )
    public static boolean isEmpty( String ... strings )
    {
        if (( strings == null ) || ( strings.length < 1 )) {
            return true;
        }

        for ( String s : strings )
        {
            if (( s == null ) || ( s.trim().length() < 1 ))
            {
                return true;
            }
        }

        return false;
    }


    /**
     * Verifies neither of Strings specified is null or empty.
     * @return first String provided
     * @throws java.lang.IllegalArgumentException
     */
    @SuppressWarnings( "ReturnOfNull" )
    public static String verifyNotEmpty( String ... strings )
    {
        if ( isEmpty( strings ))
        {
            throw new IllegalArgumentException( String.format(
                "Some String arguments are null or empty: %s", Arrays.toString( strings )));
        }

        return strings[ 0 ];
    }
}
