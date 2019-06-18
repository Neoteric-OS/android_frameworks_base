This is a tool for launching applications using the system launcher. This has
the effect of aligning app launch to a vsync, which reduces our variation
significantly and better matches real use cases.

Example usage:

    launchtool "Gallery"
