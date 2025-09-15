interface AuthorInfoProps {
  author: string;
}

const AuthorInfo: React.FC<AuthorInfoProps> = ({ author }) => {
  return (
    <div className="bg-background rounded-lg shadow-lg p-8 mb-0">
      <div className="flex flex-col md:flex-row items-center gap-8">
        <img src="/images/editora.webp" alt={author} className="w-32 h-32 rounded-full shadow-md" />
        <div>
          <h2 className="text-2xl font-bold text-primary mb-4">{author}</h2>
          <p className="text-lg text-text-secondary leading-relaxed">
            A Editora Nosso Lar é uma empresa de livros espiritas fundadas em 2013. Recentemente, atualizamos nosso ecomercce para melhor atender os nossos clientes.
          </p>
        </div>
      </div>
    </div>
  );
};

export default AuthorInfo;
