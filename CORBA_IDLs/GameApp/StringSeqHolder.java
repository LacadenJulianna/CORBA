package GameApp;


/**
* GameApp/StringSeqHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from Game.idl
* Sunday, 1 June 2025 7:33:27 PM AWST
*/

public final class StringSeqHolder implements org.omg.CORBA.portable.Streamable
{
  public String value[] = null;

  public StringSeqHolder ()
  {
  }

  public StringSeqHolder (String[] initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = GameApp.StringSeqHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    GameApp.StringSeqHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return GameApp.StringSeqHelper.type ();
  }

}
