package io.github.qingshu.mcptool.common

/**
 * A builder for creating operating system processes.
 * This is an expect class that is platform-specific in actual implementations.
 * @param command the command and its arguments to execute
 */
expect class ProcessBuilder(vararg command: String) {
    /**
     * Sets the working directory for the process.
     * @param dir the directory path
     * @return this builder for chaining
     */
    fun directory(dir: String): ProcessBuilder
    /**
     * Whether to merge the standard error stream with the standard output stream.
     * @param merge true to merge stderr with stdout, false otherwise
     * @return this builder for chaining
     */
    fun mergeStderr(merge: Boolean): ProcessBuilder
    /**
     * Sets an environment variable for the process.
     * @param key the environment variable name
     * @param value the environment variable value
     * @return this builder for chaining
     */
    fun environment(key: String, value: String): ProcessBuilder
    /**
     * Starts the process.
     * @return the started process
     */
    fun start(): Process
}
