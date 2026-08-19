package com.haylent.mobwizardry.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable rewards for winning a raid or defeating a summoned boss. Each configured command
 * is executed for every player in the winning dimension, with {@code %player%} replaced by that
 * player's name, and {@code message} (if set) is broadcast. Commands run as the server, so they
 * can give items, XP, effects, loot, etc. - same authority as every other config file.
 */
public class Rewards
{
    /** Broadcast to everyone when the rewards are granted (empty = silent). */
    public String message = "";

    /** Commands to run per player; {@code %player%} is replaced with the player's name. */
    public List<String> commands = new ArrayList<>();

    /**
     * Whether this rewards block has anything to grant (a message or at least one non-blank
     * command).
     */
    public boolean hasContent()
    {
        if (message != null && !message.isBlank())
        {
            return true;
        }
        if (commands != null)
        {
            for (String command : commands)
            {
                if (command != null && !command.isBlank())
                {
                    return true;
                }
            }
        }
        return false;
    }
}