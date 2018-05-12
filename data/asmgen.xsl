<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:template match="asmgen">
        <html>
            <body>
                <table>
                    <xsl:apply-templates/>
                </table>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="asmoper">
        <tr bgcolor="00BBFF">
            <td align="center">
                <nobr>
                    <xsl:text>&#xA0;</xsl:text>
                    <xsl:apply-templates select="@label"/>
                    <xsl:text>&#xA0;</xsl:text>
                </nobr>
            </td>
            <td align="left">
                <nobr>
                    <xsl:text>&#xA0;</xsl:text>
                    <font style="font-family:arial black">
                        <xsl:value-of select="@token"/>
                    </font>
                    <xsl:text>&#xA0;</xsl:text>
                </nobr>
            </td>
        </tr>
    </xsl:template>

    <xsl:template match="asmcomment">
        <tr bgcolor="00BBFF">
            <td align="center">
                <nobr>
                    <xsl:text>&#xA0;</xsl:text>
                    <xsl:apply-templates select="@mmix_label"/>
                    <xsl:text>&#xA0;</xsl:text>
                </nobr>
            </td>
        </tr>
    </xsl:template>

    <xsl:template match="asmlabel">
        <tr bgcolor="00BBFF">
            <td align="center">
                <nobr>
                    <xsl:text>&#xA0;</xsl:text>
                    <font style="font-family:arial black">
                    <xsl:apply-templates select="@label"/>
                    </font>
                    <xsl:text>&#xA0;</xsl:text>
                </nobr>
            </td>
            <td align="right">
                <nobr>
                    <xsl:text>&#xA0;</xsl:text>
                    <font style="font-family:arial black">
                        <xsl:value-of select="@token"/>
                    </font>
                    <xsl:text>&#xA0;</xsl:text>
                </nobr>
            </td>
        </tr>
    </xsl:template>

    <xsl:template match="mmix_label">
        <nobr>
            <font style="font-family:arial black">
                <xsl:value-of select="label"/>
            </font>
        </nobr>
    </xsl:template>

</xsl:stylesheet>
