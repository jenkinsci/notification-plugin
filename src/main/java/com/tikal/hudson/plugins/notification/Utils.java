package com.tikal.hudson.plugins.notification;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;


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
     * @param strings - Strings to check for empty (whitespace is trimmed) or null.
     * @return True if any string is empty
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
     * @param strings Strings to check for empty (whitespace is trimmed) or null.
     * @throws java.lang.IllegalArgumentException Throws this exception if any string is empty.
     */
    @SuppressWarnings( "ReturnOfNull" )
    public static void verifyNotEmpty( String ... strings )
    {
        if ( isEmpty( strings ))
        {
            throw new IllegalArgumentException( String.format(
                "Some String arguments are null or empty: %s", Arrays.toString( strings )));
        }
    }

    /**
     * Get the actual URL from the credential id
     * @param credentialId Credential id to lookup
     * @param itemGroup the item group to look for the credential
     * @return Actual URL
     */
    public static String getSecretUrl(String credentialId, ItemGroup itemGroup) {
        // Grab the secret text
        StringCredentials creds = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(
                StringCredentials.class, itemGroup, ACL.SYSTEM, Collections.emptyList()),
                CredentialsMatchers.withId(credentialId));
        if (creds == null) {
            return null;
        }
        Secret secretUrl = creds.getSecret();
        return secretUrl.getPlainText();
    }


}
